using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

// 容大 RLS1000 PLU 回收桥接（32 位，CWD 必须为含 rtslabelscale.dll + SYSTEM.CFG 的目录）
// 用法: RongtaPluBridge.exe <ip> <outFile.ndjson>
// 退出码: >=0 = 回收条数; <0 = 失败
class Bridge
{
    [DllImport("rtslabelscale.dll", CallingConvention = CallingConvention.StdCall, CharSet = CharSet.Ansi, EntryPoint = "rtscaleLoadIniFile")]
    public static extern int rtscaleLoadIniFile(string cfg);

    [DllImport("rtslabelscale.dll", CallingConvention = CallingConvention.StdCall, CharSet = CharSet.Ansi, EntryPoint = "rtscaleConnect")]
    public static extern int rtscaleConnect(string addr, int baud, ref int connid);

    [DllImport("rtslabelscale.dll", CallingConvention = CallingConvention.StdCall, EntryPoint = "rtscaleDisConnect")]
    public static extern int rtscaleDisConnect(int connid);

    [DllImport("rtslabelscale.dll", CallingConvention = CallingConvention.StdCall, EntryPoint = "rtscaleUploadPluData")]
    public static extern int rtscaleUploadPluData(int connid, IntPtr p);

    public delegate void Callback([MarshalAs(UnmanagedType.LPStr)] string sResult, int iRecNO, int ACount);

    static readonly object LockObj = new object();
    static StreamWriter Writer;
    static int Count = 0;

    static void PluCallback(string sResult, int iRecNO, int ACount)
    {
        try
        {
            lock (LockObj)
            {
                // 压平为单行 JSON（NDJSON），便于下游逐行解析
                string flat = sResult.Replace("\r", "").Replace("\n", "");
                Writer.WriteLine(flat);
                Writer.Flush();
                Count++;
            }
        }
        catch (Exception e)
        {
            Console.Error.WriteLine("CB-EXC " + e);
        }
    }

    static int Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.Error.WriteLine("usage: RongtaPluBridge <ip> <outFile>");
            return -99;
        }
        string ip = args[0];
        string outFile = args[1];
        try
        {
            int r = rtscaleLoadIniFile(@".\SYSTEM.CFG");
            Console.WriteLine("LoadIniFile=" + r);
            int connid = 0;
            int c = rtscaleConnect(ip, 0, ref connid);
            Console.WriteLine("Connect=" + c + " connid=" + connid);
            if (c < 0) return -1;
            using (Writer = new StreamWriter(outFile, false, new UTF8Encoding(false)))
            {
                Callback d = PluCallback;
                IntPtr p = Marshal.GetFunctionPointerForDelegate(d);
                int ret = rtscaleUploadPluData(connid, p);
                GC.KeepAlive(d);
                Console.WriteLine("UploadRet=" + ret + " lines=" + Count);
                for (int i = 0; i < 6; i++)
                {
                    System.Threading.Thread.Sleep(1000);
                    Console.WriteLine("wait{0} lines={1}", i + 1, Count);
                    if (Count >= ret && ret > 0) break;
                }
                rtscaleDisConnect(connid);
                return ret >= 0 ? Count : ret;
            }
        }
        catch (Exception e)
        {
            Console.Error.WriteLine("EXC " + e);
            return -98;
        }
    }
}
