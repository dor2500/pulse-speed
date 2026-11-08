using System;
using System.Drawing;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace PulseSpeed
{
    static class Program
    {
        private static HttpListener listener;
        private static int port;
        private static Thread serverThread;

        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // Start embedded local HTTP server
            StartLocalServer();

            // Run Form
            Application.Run(new MainWindow(string.Format("http://127.0.0.1:{0}/index.html", port)));

            // Cleanup
            StopLocalServer();
        }

        private static void StartLocalServer()
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

            if (listener == null) return;

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
        }

        private static void ProcessRequest(HttpListenerContext context)
        {
            var req = context.Request;
            var res = context.Response;

            string path = req.Url.AbsolutePath.TrimStart('/');
            if (string.IsNullOrEmpty(path)) path = "index.html";

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

            // Fallback to disk if running from source folder
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

            if (path.EndsWith(".html")) mime = "text/html; charset=utf-8";
            else if (path.EndsWith(".css")) mime = "text/css; charset=utf-8";
            else if (path.EndsWith(".js")) mime = "application/javascript; charset=utf-8";
            else if (path.EndsWith(".png")) mime = "image/png";
            else if (path.EndsWith(".ico")) mime = "image/x-icon";

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

    public class MainWindow : Form
    {
        private WebBrowser browser;

        public MainWindow(string url)
        {
            Text = "Pulse Speed Test";
            Width = 980;
            Height = 740;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(14, 21, 20); // Dark theme matching Pulse

            try
            {
                var iconStream = Assembly.GetExecutingAssembly().GetManifestResourceStream("app.ico");
                if (iconStream != null)
                {
                    Icon = new Icon(iconStream);
                }
            }
            catch { }

            browser = new WebBrowser
            {
                Dock = DockStyle.Fill,
                IsWebBrowserContextMenuEnabled = false,
                ScriptErrorsSuppressed = true,
                AllowWebBrowserDrop = false,
            };

            Controls.Add(browser);

            // Handle Key shortcuts
            KeyPreview = true;
            KeyDown += (s, e) =>
            {
                if (e.KeyCode == Keys.F11)
                {
                    if (FormBorderStyle == FormBorderStyle.None)
                    {
                        FormBorderStyle = FormBorderStyle.Sizable;
                        WindowState = FormWindowState.Normal;
                    }
                    else
                    {
                        FormBorderStyle = FormBorderStyle.None;
                        WindowState = FormWindowState.Maximized;
                    }
                }
                else if (e.KeyCode == Keys.F5)
                {
                    browser.Refresh();
                }
            };

            Load += (s, e) =>
            {
                // Attempt to set FeatureControl IE11 / Edge mode for WebBrowser
                SetWebBrowserFeatureControl();
                browser.Navigate(url);
            };
        }

        private void SetWebBrowserFeatureControl()
        {
            try
            {
                string appName = Path.GetFileName(Application.ExecutablePath);
                using (var key = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(
                    @"Software\Microsoft\Internet Explorer\Main\FeatureControl\FEATURE_BROWSER_EMULATION"))
                {
                    if (key != null) key.SetValue(appName, 11001, Microsoft.Win32.RegistryValueKind.DWord);
                }
            }
            catch { }
        }
    }
}
