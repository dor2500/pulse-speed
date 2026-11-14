package com.dorp.pulse.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random

enum class DnsType(val code: Int) { A(1), AAAA(28), CNAME(5), MX(15), TXT(16), NS(2), SOA(6), PTR(12) }

data class DnsRecord(val name: String, val type: String, val ttl: Long, val value: String)

data class DnsAnswer(
    val server: String,
    val ms: Double,
    /** RCODE text: NOERROR, NXDOMAIN, SERVFAIL, REFUSED… */
    val status: String,
    val records: List<DnsRecord>,
    val viaTcp: Boolean,
)

class DnsException(message: String) : Exception(message)

/**
 * A small DNS client (RFC 1035) that talks straight to a resolver over UDP, retrying over TCP when the
 * answer is truncated. Used by the DNS lookup and DNS benchmark tools, so the timing is the resolver's,
 * not the phone's cache.
 */
object DnsClient {

    suspend fun query(server: String, name: String, type: DnsType, timeoutMs: Int = 2500): DnsAnswer = withContext(Dispatchers.IO) {
        val addr = InetAddress.getByName(server.trim())
        val id = Random.nextInt(0x10000)
        val packet = build(id, name.trim().trimEnd('.'), type.code)
        val t0 = System.nanoTime()
        var reply = udp(addr, packet, id, timeoutMs)
        var tcp = false
        if (reply.size > 2 && (reply[2].toInt() and 0x02) != 0) { // TC bit
            reply = tcp(addr, packet, timeoutMs); tcp = true
        }
        val ms = (System.nanoTime() - t0) / 1e6
        parse(reply, addr.hostAddress ?: server, ms, tcp)
    }

    /** Reverse-lookup name for an IPv4/IPv6 address (in-addr.arpa / ip6.arpa). */
    fun reverseName(ip: String): String? = runCatching {
        val a = InetAddress.getByName(ip)
        val b = a.address
        if (b.size == 4) b.reversed().joinToString(".") { (it.toInt() and 0xff).toString() } + ".in-addr.arpa"
        else b.reversed().joinToString(".") { v -> val x = v.toInt() and 0xff; "${Integer.toHexString(x and 0xf)}.${Integer.toHexString(x shr 4)}" } + ".ip6.arpa"
    }.getOrNull()

    private fun build(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0))
        if (name.isNotEmpty()) name.split('.').forEach { label ->
            val bytes = java.net.IDN.toASCII(label).toByteArray(Charsets.US_ASCII)
            if (bytes.isEmpty() || bytes.size > 63) throw DnsException("Invalid name")
            out.write(bytes.size); out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf((type shr 8).toByte(), type.toByte(), 0, 1))
        return out.toByteArray()
    }

    private fun udp(addr: InetAddress, query: ByteArray, id: Int, timeoutMs: Int): ByteArray {
        DatagramSocket().use { s ->
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            s.send(DatagramPacket(query, query.size, addr, 53))
            val buf = ByteArray(4096)
            while (true) {
                val left = ((deadline - System.nanoTime()) / 1_000_000).toInt()
                if (left <= 0) throw DnsException("Timed out")
                s.soTimeout = left
                val p = DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (e: SocketTimeoutException) { throw DnsException("Timed out") }
                if (p.length >= 12 && ((buf[0].toInt() and 0xff) shl 8 or (buf[1].toInt() and 0xff)) == id) return buf.copyOf(p.length)
            }
        }
    }

    private fun tcp(addr: InetAddress, query: ByteArray, timeoutMs: Int): ByteArray {
        Socket().use { s ->
            s.connect(InetSocketAddress(addr, 53), timeoutMs); s.soTimeout = timeoutMs
            val out = DataOutputStream(s.getOutputStream()); out.writeShort(query.size); out.write(query); out.flush()
            val inp = DataInputStream(s.getInputStream()); val len = inp.readUnsignedShort()
            return ByteArray(len).also { inp.readFully(it) }
        }
    }

    private val RCODES = mapOf(0 to "NOERROR", 1 to "FORMERR", 2 to "SERVFAIL", 3 to "NXDOMAIN", 4 to "NOTIMP", 5 to "REFUSED")

    private fun parse(b: ByteArray, server: String, ms: Double, tcp: Boolean): DnsAnswer {
        fun u16(i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
        fun u32(i: Int) = (u16(i).toLong() shl 16) or u16(i + 2).toLong()
        fun readName(start: Int): Pair<String, Int> {
            val labels = ArrayList<String>(); var i = start; var end = -1; var jumps = 0
            while (true) {
                val len = b[i].toInt() and 0xff
                when {
                    len == 0 -> { i++; break }
                    len and 0xc0 == 0xc0 -> {
                        if (end < 0) end = i + 2
                        i = ((len and 0x3f) shl 8) or (b[i + 1].toInt() and 0xff)
                        if (++jumps > 20) throw DnsException("Bad compression")
                    }
                    else -> { labels += String(b, i + 1, len, Charsets.ISO_8859_1); i += len + 1 }
                }
            }
            return (if (labels.isEmpty()) "." else labels.joinToString(".")) to (if (end >= 0) end else i)
        }
        val rcode = u16(2) and 0xf
        val qd = u16(4); val an = u16(6)
        var i = 12
        repeat(qd) { i = readName(i).second + 4 }
        val records = ArrayList<DnsRecord>()
        repeat(an) {
            val (name, after) = readName(i); i = after
            val type = u16(i); val ttl = u32(i + 4); val rdlen = u16(i + 8); val rd = i + 10
            val value = when (type) {
                1 -> (0 until 4).joinToString(".") { (b[rd + it].toInt() and 0xff).toString() }
                28 -> InetAddress.getByAddress(b.copyOfRange(rd, rd + 16)).hostAddress ?: ""
                5, 2, 12 -> readName(rd).first
                15 -> "${u16(rd)} ${readName(rd + 2).first}"
                16 -> buildString {
                    var j = rd
                    while (j < rd + rdlen) { val l = b[j].toInt() and 0xff; append(String(b, j + 1, l, Charsets.UTF_8)); j += l + 1 }
                }
                6 -> { val (m, a) = readName(rd); val (r, a2) = readName(a); "$m $r serial ${u32(a2)}" }
                else -> "${rdlen} bytes"
            }
            val typeName = DnsType.entries.firstOrNull { it.code == type }?.name ?: "TYPE$type"
            records += DnsRecord(name, typeName, ttl, value)
            i = rd + rdlen
        }
        return DnsAnswer(server, ms, RCODES[rcode] ?: "RCODE$rcode", records, tcp)
    }
}
