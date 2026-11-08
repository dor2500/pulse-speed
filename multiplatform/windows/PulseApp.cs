using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Threading;

namespace PulseSpeed
{
    static class Program
    {
        private static HttpListener listener;
        private static int port;
        private static Thread serverThread;
        private static DateTime lastHeartbeat = DateTime.UtcNow;
        private static bool hasReceivedFirstHeartbeat = false;

        [STAThread]
        static void Main(string[] args)
        {
            if (!StartLocalServer())
            {
                return;
            }

            string url = string.Format("http://127.0.0.1:{0}/index.html", port);
            string browserPath = FindChromiumBrowser();
            Process browserProc = null;

            if (!string.IsNullOrEmpty(browserPath))
            {
                string profileDir = Path.Combine(Path.GetTempPath(), "PulseProfile");
                var psi = new ProcessStartInfo
                {
                    FileName = browserPath,
                    Arguments = string.Format("--app=\"{0}\" --user-data-dir=\"{1}\" --window-size=980,740", url, profileDir),
                    UseShellExecute = false
                };

                try
                {
                    browserProc = Process.Start(psi);
                }
                catch { browserProc = null; }
            }

            if (browserProc == null)
            {
                // Fallback to default browser
                try
                {
                    Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
                }
                catch { }
            }

            // Monitor heartbeat loop: keep server alive until window is closed
            DateTime startTime = DateTime.UtcNow;
            while (true)
            {
                Thread.Sleep(1000);

                if (browserProc != null && browserProc.HasExited)
                {
                    break;
                }

                // If heartbeat was received, shut down when browser closes tab/window (no heartbeats for 5s)
                if (hasReceivedFirstHeartbeat)
                {
                    if ((DateTime.UtcNow - lastHeartbeat).TotalSeconds > 5)
                    {
                        break;
                    }
                }
                else
                {
                    // If no heartbeat received within 30 seconds of launch, assume failed or closed
                    if ((DateTime.UtcNow - startTime).TotalSeconds > 30)
                    {
                        break;
                    }
                }
            }

            StopLocalServer();
        }

        private static string FindChromiumBrowser()
        {
            string[] paths = new string[]
            {
                @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Google\Chrome\Application\chrome.exe",
                @"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Microsoft\Edge\Application\msedge.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Google\Chrome\Application\chrome.exe")
            };

            foreach (var p in paths)
            {
                if (File.Exists(p)) return p;
            }
            return null;
        }

        private static bool StartLocalServer()
        {
            var rnd = new Random();
            for (int i = 0; i < 20; i++)
            {
                port = rnd.Next(28000, 39000);
                try
                {
                    listener = new HttpListener();
                    listener.Prefixes.Add(string.Format("http://127.0.0.1:{0}/", port));
                    listener.Start();
                    break;
                }
                catch
                {
                    listener = null;
                }
            }

            if (listener == null) return false;

            serverThread = new Thread(() =>
            {
                while (listener != null && listener.IsListening)
                {
                    try
                    {
                        var context = listener.GetContext();
                        ThreadPool.QueueUserWorkItem((_) => ProcessRequest(context));
                    }
                    catch { break; }
                }
            })
            { IsBackground = true };
            serverThread.Start();
            return true;
        }

        private static void ProcessRequest(HttpListenerContext context)
        {
            var req = context.Request;
            var res = context.Response;

            string path = req.Url.AbsolutePath.TrimStart('/');
            if (string.IsNullOrEmpty(path)) path = "index.html";

            // Heartbeat
            if (path.Equals("heartbeat", StringComparison.OrdinalIgnoreCase))
            {
                lastHeartbeat = DateTime.UtcNow;
                hasReceivedFirstHeartbeat = true;
                byte[] ok = Encoding.UTF8.GetBytes("OK");
                res.ContentType = "text/plain";
                res.OutputStream.Write(ok, 0, ok.Length);
                res.Close();
                return;
            }

            byte[] buffer = null;
            string mime = "text/plain";

            var asm = Assembly.GetExecutingAssembly();
            string resName = null;
            foreach (var name in asm.GetManifestResourceNames())
            {
                if (name.EndsWith("." + path, StringComparison.OrdinalIgnoreCase) ||
                    name.EndsWith(path, StringComparison.OrdinalIgnoreCase))
                {
                    resName = name;
                    break;
                }
            }

            if (resName != null)
            {
                using (var s = asm.GetManifestResourceStream(resName))
                {
                    if (s != null)
                    {
                        buffer = new byte[s.Length];
                        s.Read(buffer, 0, buffer.Length);
                    }
                }
            }

            // Fallback to disk
            if (buffer == null)
            {
                string localFile = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, path);
                if (File.Exists(localFile))
                {
                    buffer = File.ReadAllBytes(localFile);
                }
            }

            if (buffer == null)
            {
                res.StatusCode = 404;
                res.Close();
                return;
            }

            // Inject heartbeat script into HTML
            if (path.EndsWith(".html", StringComparison.OrdinalIgnoreCase))
            {
                mime = "text/html; charset=utf-8";
                string html = Encoding.UTF8.GetString(buffer);
                string heartbeatScript = "<script>setInterval(function(){fetch('/heartbeat').catch(function(){});}, 2000);</script></body>";
                html = html.Replace("</body>", heartbeatScript);
                buffer = Encoding.UTF8.GetBytes(html);
            }
            else if (path.EndsWith(".css", StringComparison.OrdinalIgnoreCase)) mime = "text/css; charset=utf-8";
            else if (path.EndsWith(".js", StringComparison.OrdinalIgnoreCase)) mime = "application/javascript; charset=utf-8";
            else if (path.EndsWith(".png", StringComparison.OrdinalIgnoreCase)) mime = "image/png";
            else if (path.EndsWith(".ico", StringComparison.OrdinalIgnoreCase)) mime = "image/x-icon";

            res.ContentType = mime;
            res.ContentLength64 = buffer.Length;
            res.Headers.Add("Access-Control-Allow-Origin", "*");
            res.OutputStream.Write(buffer, 0, buffer.Length);
            res.Close();
        }

        private static void StopLocalServer()
        {
            try
            {
                if (listener != null)
                {
                    listener.Stop();
                    listener.Close();
                }
            }
            catch { }
        }
    }
}
