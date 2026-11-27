// YouSheng JHScale.dll bridge (32bit) - ScalePlex driver toolchain
// CWD must contain JHScale.dll (+ JHScale.ini optional).
// usage:
//   YouShengBridge.exe probe    <ip:port>            (fast connect-only probe)
//   YouShengBridge.exe gettime  <ip:port>
//   YouShengBridge.exe settime  <ip:port>            (write PC now to scale)
//   YouShengBridge.exe getplu   <ip:port> <out.ndjson>
//   YouShengBridge.exe setplu   <ip:port> <in.ndjson>
//   YouShengBridge.exe clearplu <ip:port> [pluId]    (id=0 or omitted: clear all)
//   YouShengBridge.exe getreport <ip:port> <out.ndjson>
//   YouShengBridge.exe clearreport <ip:port>
// exit code 0 = OK; NDJSON: one PLU/report per line.
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

public struct TIME { public int Year, Month, Day, Hour, Minute, Second; }

[StructLayout(LayoutKind.Sequential, Pack = 4)]
public struct PLU
{
    public int ID;
    public int Remark;
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 21)] public byte[] Index;   // char[21]
    public int Unit;
    public double Price, Cost, Tare;
    public int Label1, BarT1, BarF1, Label2, BarT2, BarF2;
    public int Class;
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 2048)] public byte[] Text;  // char[8][256]
    public int PS_SD, PS_ST, PS_PD, PS_PT, PS_UD;
    public int PC_SD, PC_ST, PC_PD, PC_PT, PC_UD;
    public int DF_D, DF_U;
    public double DF_DN, DF_UN;

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    public struct DA
    {
        public int Sort, Day;
        public double Dn, Up, Num;
    }
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 4)] public DA[] da;         // DA[4]
    public int TaxSort, TaxData;
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 4)] public int[] SspValue;
}

[StructLayout(LayoutKind.Sequential, Pack = 4)]
public struct REPORT
{
    public int Year, Month, Day, Hour, Minute, Second;
    public int FID, SID, Salesman, SubID;
    public double Price;
    public int PLU, Class, Dept;
    public double Cost, Count;
    public int Unit, Reject;
    public double Tax;
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 1024)] public byte[] Text;  // char[4][256]
    public double UnitPrice;
    [MarshalAs(UnmanagedType.ByValArray, SizeConst = 16)] public byte[] Mark;    // char[16]
}

class YouShengBridge
{
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern IntPtr JH_Connect(string ip, int connectTimeout, int recvTimeout, int sendTimeout, int recvSize, int sendSize);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_IsConnected(IntPtr h);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern void JH_Close(IntPtr h);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetTime(IntPtr h, ref TIME t);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_SetTime(IntPtr h, ref TIME t);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_SetFirstPLU();
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_SetNextPLU(ref PLU p);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_SetPLU(IntPtr h);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetPLU(IntPtr h, uint id);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetFirstPLU(IntPtr h);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetNextPLU(IntPtr h, ref PLU p);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_ClearPLU(IntPtr h, uint id);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetReport(IntPtr h, uint fid);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetFirstReport(IntPtr h);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetNextReport(IntPtr h, ref REPORT r);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_ClearReport(IntPtr h, uint id);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern void JH_InitInfo(ref INFO p);
    [DllImport("JHScale.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int JH_GetInfo(IntPtr h, ref INFO p);

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    public struct INFO
    {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string firmware;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string font;
        public int connectType;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string shopName;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string machineName;
        public int machineNumber;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string modalName;
    }

    static int Main(string[] args)
    {
        try
        {
            if (args.Length < 2) { Console.WriteLine("usage: YouShengBridge <cmd> <ip:port> [file|id]"); return -99; }
            string cmd = args[0], target = args[1];
            bool probe = cmd == "probe";
            int connT = probe ? 2000 : 5000;   // probe: short timeouts for heartbeat / auto-detect
            var h = JH_Connect(target, connT, probe ? 3000 : 8000, connT, 65536, 65536);
            Console.WriteLine("handle=" + h);
            if (h == IntPtr.Zero || JH_IsConnected(h) == 0) { Console.WriteLine("RESULT=CONNECT_FAIL"); return -1; }
            if (probe) { Console.WriteLine("RESULT=OK"); JH_Close(h); return 0; }
            int rv;
            switch (cmd)
            {
                case "gettime":
                    var t = new TIME();
                    rv = JH_GetTime(h, ref t);
                    Console.WriteLine("time=" + string.Format("{0:D4}-{1:D2}-{2:D2} {3:D2}:{4:D2}:{5:D2}", t.Year, t.Month, t.Day, t.Hour, t.Minute, t.Second) + " rv=" + rv);
                    rv = rv == 1 ? 0 : rv;
                    break;
                case "settime":
                    var now = DateTime.Now;
                    var st = new TIME { Year = now.Year, Month = now.Month, Day = now.Day, Hour = now.Hour, Minute = now.Minute, Second = now.Second };
                    rv = JH_SetTime(h, ref st);
                    rv = rv == 1 ? 0 : rv;
                    break;
                case "getplu":
                    rv = GetPlu(h, args[2]);
                    break;
                case "setplu":
                    rv = SetPlu(h, args[2]);
                    break;
                case "clearplu":
                    rv = JH_ClearPLU(h, args.Length > 2 ? uint.Parse(args[2]) : 0u);
                    Console.WriteLine("clearplu=" + rv);
                    rv = rv == 1 ? 0 : rv;
                    break;
                case "getreport":
                    rv = GetReport(h, args[2]);
                    break;
                case "clearreport":
                    rv = JH_ClearReport(h, 0u);
                    Console.WriteLine("clearreport=" + rv);
                    rv = rv == 1 ? 0 : rv;
                    break;
                case "getinfo":
                    var info = new INFO();
                    JH_InitInfo(ref info);
                    rv = JH_GetInfo(h, ref info);
                    Console.WriteLine("firmware=" + info.firmware);
                    Console.WriteLine("font=" + info.font);
                    Console.WriteLine("connectType=" + info.connectType);
                    Console.WriteLine("shopName=" + info.shopName);
                    Console.WriteLine("machineName=" + info.machineName);
                    Console.WriteLine("machineNumber=" + info.machineNumber);
                    Console.WriteLine("modalName=" + info.modalName);
                    Console.WriteLine("rv=" + rv);
                    rv = 0;
                    break;
                default:
                    Console.WriteLine("unknown cmd " + cmd);
                    rv = -98;
                    break;
            }
            JH_Close(h);
            Console.WriteLine("rv=" + rv);
            Console.WriteLine("RESULT=" + (rv == 0 ? "OK" : "FAIL"));
            return rv;
        }
        catch (Exception e)
        {
            Console.WriteLine("EXC=" + e.Message);
            return -97;
        }
    }

    static int GetPlu(IntPtr h, string outFile)
    {
        int rv = JH_GetPLU(h, 0u);   // 0 = all PLUs; doc: 1 = ok (this scale firmware returns 0 even when ok, cf. JH_GetTime)
        Console.WriteLine("getPLU=" + rv);
        int total = JH_GetFirstPLU(h);   // returns record count
        Console.WriteLine("total=" + total);
        if (total <= 0) { File.WriteAllText(outFile, "", new UTF8Encoding(false)); Console.WriteLine("count=0"); return rv == 1 || rv == 0 ? 0 : -rv - 1; }
        int count = 0;
        using (var w = new StreamWriter(outFile, false, new UTF8Encoding(false)))
        {
            for (int i = 0; i < total; i++)
            {
                var p = default(PLU);
                if (JH_GetNextPLU(h, ref p) != 1) break;
                w.WriteLine(PluJson(ref p));
                count++;
            }
        }
        Console.WriteLine("count=" + count);
        return 0;
    }

    static int SetPlu(IntPtr h, string inFile)
    {
        var list = new List<PLU>();
        foreach (var line in File.ReadAllLines(inFile, Encoding.UTF8))
        {
            if (line.Trim().Length == 0) continue;
            list.Add(ParsePlu(line));
        }
        if (list.Count == 0) { Console.WriteLine("count=0"); return 0; }
        JH_SetFirstPLU();
        foreach (var item in list)
        {
            PLU p = item;
            JH_SetNextPLU(ref p);
        }
        int rv = JH_SetPLU(h);
        Console.WriteLine("setPLU=" + rv + " count=" + list.Count);
        return rv == 1 ? 0 : rv;
    }

    static int GetReport(IntPtr h, string outFile)
    {
        int rv = JH_GetReport(h, 1u);   // returns max FID, 0 = none/fail
        Console.WriteLine("getReport=" + rv);
        if (rv <= 0) { File.WriteAllText(outFile, "", new UTF8Encoding(false)); Console.WriteLine("count=0"); return 0; }
        int total = JH_GetFirstReport(h);
        Console.WriteLine("total=" + total);
        if (total <= 0) { File.WriteAllText(outFile, "", new UTF8Encoding(false)); Console.WriteLine("count=0"); return 0; }
        int count = 0;
        using (var w = new StreamWriter(outFile, false, new UTF8Encoding(false)))
        {
            for (int i = 0; i < total; i++)
            {
                var r = default(REPORT);
                if (JH_GetNextReport(h, ref r) != 1) break;
                w.WriteLine(ReportJson(ref r));
                count++;
            }
        }
        Console.WriteLine("count=" + count);
        return 0;
    }

    static string Esc(string s)
    {
        return s == null ? "" : s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "");
    }

    static string TextAt(byte[] text, int idx)
    {
        var seg = new byte[256];
        Array.Copy(text, idx * 256, seg, 0, 256);
        int len = Array.IndexOf(seg, (byte)0);
        if (len < 0) len = 256;
        return Encoding.GetEncoding("GBK").GetString(seg, 0, len).TrimEnd();
    }

    static string PluJson(ref PLU p)
    {
        var sb = new StringBuilder();
        sb.Append("{\"ID\": ").Append(p.ID);
        sb.Append(", \"Index\": \"").Append(Esc(p.Index == null ? "" : Encoding.GetEncoding("GBK").GetString(p.Index).TrimEnd('\0'))).Append("\"");
        sb.Append(", \"Unit\": ").Append(p.Unit);
        sb.Append(", \"Price\": ").Append(p.Price.ToString("R", CultureInfo.InvariantCulture));
        sb.Append(", \"Class\": ").Append(p.Class);
        for (int i = 0; i < 8; i++)
        {
            string v = TextAt(p.Text, i);
            if (v.Length > 0) sb.Append(", \"Text").Append(i + 1).Append("\": \"").Append(Esc(v)).Append("\"");
        }
        sb.Append(", \"PS_SD\": ").Append(p.PS_SD).Append(", \"PS_ST\": ").Append(p.PS_ST);
        sb.Append("}");
        return sb.ToString();
    }

    static string ReportJson(ref REPORT r)
    {
        var sb = new StringBuilder();
        sb.Append("{\"FID\": ").Append(r.FID).Append(", \"dt\": \"");
        sb.Append(string.Format("{0:D4}-{1:D2}-{2:D2} {3:D2}:{4:D2}:{5:D2}", r.Year, r.Month, r.Day, r.Hour, r.Minute, r.Second));
        sb.Append("\", \"PLU\": ").Append(r.PLU).Append(", \"Dept\": ").Append(r.Dept);
        sb.Append(", \"UnitPrice\": ").Append(r.UnitPrice.ToString("R", CultureInfo.InvariantCulture));
        sb.Append(", \"Price\": ").Append(r.Price.ToString("R", CultureInfo.InvariantCulture));
        sb.Append(", \"Count\": ").Append(r.Count.ToString("R", CultureInfo.InvariantCulture));
        sb.Append(", \"SID\": ").Append(r.SID);
        sb.Append("}");
        return sb.ToString();
    }

    static byte[] GbkBytes(string s, int size)
    {
        var b = new byte[size];
        if (s != null)
        {
            var enc = Encoding.GetEncoding("GBK");
            var src = enc.GetBytes(s);
            Array.Copy(src, b, Math.Min(src.Length, size));
        }
        return b;
    }

    static PLU ParsePlu(string json)
    {
        var p = new PLU();
        p.ID = GetInt(json, "ID");
        p.Remark = GetInt(json, "Remark");
        p.Index = GbkBytes(GetStr(json, "Index"), 21);
        p.Unit = GetInt(json, "Unit", 1);
        p.Price = GetDbl(json, "Price");
        p.Cost = GetDbl(json, "Cost");
        p.Tare = GetDbl(json, "Tare");
        p.Label1 = GetInt(json, "Label1", 1);
        p.Class = GetInt(json, "Class");
        p.Text = new byte[2048];
        var name = GetStr(json, "Text1");
        if (name != null)
        {
            var nb = Encoding.GetEncoding("GBK").GetBytes(name);
            Array.Copy(nb, p.Text, Math.Min(nb.Length, 256));
        }
        p.PS_SD = GetInt(json, "PS_SD", 1);
        p.PS_ST = GetInt(json, "PS_ST");
        var da = new PLU.DA[4];
        for (int i = 0; i < 4; i++) da[i] = new PLU.DA();
        p.da = da;
        p.SspValue = new int[4];
        return p;
    }

    static int GetInt(string json, string key, int def = 0)
    {
        var m = System.Text.RegularExpressions.Regex.Match(json, "\"" + key + "\"\\s*:\\s*(-?\\d+)");
        return m.Success ? int.Parse(m.Groups[1].Value) : def;
    }

    static double GetDbl(string json, string key, double def = 0)
    {
        var m = System.Text.RegularExpressions.Regex.Match(json, "\"" + key + "\"\\s*:\\s*(-?[\\d.]+)");
        return m.Success ? double.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture) : def;
    }

    static string GetStr(string json, string key)
    {
        var m = System.Text.RegularExpressions.Regex.Match(json, "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        return m.Success ? m.Groups[1].Value : null;
    }
}
