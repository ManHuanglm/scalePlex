unit Unit1;

interface

uses
  Windows, Messages, SysUtils, Variants, Classes, Graphics, Controls, Forms,
  StdCtrls, Buttons, WinSock, StrUtils, Menus, JvExControls, JvComCtrls;

type
  TForm1 = class(TForm)
    lbl1: TLabel;
    lblStatus: TLabel;
    edt1: TEdit;
    edt2: TEdit;
    lbl2: TLabel;
    jvpdrsScaleIP: TJvIPAddress;
    grp1: TGroupBox;
    btnSendPLU: TButton;
    btn2: TButton;
    btn18: TButton;
    btn3: TButton;
    btn6: TBitBtn;
    btn5: TBitBtn;
    btn7: TBitBtn;
    btn8: TBitBtn;
    btn9: TBitBtn;
    btn10: TBitBtn;
    grp2: TGroupBox;
    btnReadSaleDetail: TBitBtn;
    btnDelSaleDetail: TBitBtn;
    btnReadSaleTotal: TBitBtn;
    btnDelSaleTotal: TBitBtn;
    btn17: TBitBtn;
    btn15: TBitBtn;
    btn16: TBitBtn;
    btn19: TBitBtn;
    btnReadStock: TBitBtn;
    btnSyncDT: TBitBtn;
    btn1: TBitBtn;
    btnPLUTTLTrace: TBitBtn;
    btnDelStock: TBitBtn;
    btn11: TBitBtn;
    procedure btnSendPLUClick(Sender: TObject);
    procedure btnReadSaleDetailClick(Sender: TObject);
    procedure btnReadSaleTotalClick(Sender: TObject);
    procedure btnReadStockClick(Sender: TObject);
    procedure btnDelSaleDetailClick(Sender: TObject);
    procedure btnDelSaleTotalClick(Sender: TObject);
    procedure btnDelStockClick(Sender: TObject);
    procedure btnPLUTTLTraceClick(Sender: TObject);
    procedure btn1Click(Sender: TObject);
    procedure btn2Click(Sender: TObject);
    procedure btn3Click(Sender: TObject);
    procedure btnSyncDTClick(Sender: TObject);
    procedure btn4Click(Sender: TObject);
    procedure btn5Click(Sender: TObject);
    procedure btn6Click(Sender: TObject);
    procedure btn7Click(Sender: TObject);
    procedure btn8Click(Sender: TObject);
    procedure btn9Click(Sender: TObject);
    procedure btn10Click(Sender: TObject);
    procedure btn11Click(Sender: TObject);
    procedure btn12Click(Sender: TObject);
    procedure btn15Click(Sender: TObject);
    procedure btn16Click(Sender: TObject);
    procedure btn17Click(Sender: TObject);
    procedure FormCreate(Sender: TObject);
    procedure btn19Click(Sender: TObject);
    procedure btn18Click(Sender: TObject);
  private
    { Private declarations }
  public
    { Public declarations }
  end;

var
  Form1: TForm1;

implementation

{$R *.dfm}

uses
  ScaleAPI, PLU, SalesReceipt,
  Stock, ScaleData, Password,
  PreSetKey, Traceability,
  ShopName, FlexiBarcode, SalesTotal;

procedure TForm1.btnSendPLUClick(Sender: TObject);
var
  s: TSocket; //
  Records: ReceiptPLU;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 99 do
      begin
        // 准备PLU数据
        ZeroMemory(@Records, SizeOf(Records));
        Records.PLUNo := i + 1; // PLU 编号
        Records.WeightUnit := 1; // 商品属性 0-计重 1-计件
        Records.UPriceOverride := 0; // 单价覆盖 0-禁制  1-允许
        Records.UPrice := 1223; // 单价
        Records.MG := 997; // 小分类编号
        Records.Cost := 678; // 成本价
        StrPCopy(@Records.Commodity, '商品名称');

        // 得到PLU数据 下传PLu数据
        CommResult := SendRPlu(s, @Records);
        case CommResult of // 判断返回值
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E1:
            Continue; // 写入出错
          $E2:
            Break; // 没有足够的内存空间
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btnReadSaleDetailClick(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: ReceiptDetail; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '收据号 商品流水号 状态字 PLU号 主组号 数量 重量 ' +
      '单价 折后单价 单价折扣 名义价格 实际价格 销售日期(DDMMYY) 销售时间(HHMM) 品名';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 收交易明细 函数  function ReadReceiptDetail(s: TSocket; ReceiptData: PReceiptDetail; RecordNo: Integer): Integer
        // s : socket句柄; PReceiptDetail: 销售明细数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadReceiptDetail(s, RecNo, @ReceiptRecord); // 回收交易明细数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo) + ' ' +
              // 记录编号 组成: 4位收据编号+4位项目流水号
              RightStr(IntToStr(ReceiptRecord.RecordNo), 4) + ' ' + // 项目流水号
                IntToHex(ReceiptRecord.ItemStatus, 6) + ' ' +
              // 状态字(看开头两位) 40,41:正常销售;44,45:取消;48,49:退货
              IntToStr(ReceiptRecord.PLUNo) + ' ' + // PLU 编号
                IntToStr(ReceiptRecord.MG) + ' ' + // 主组编号
                IntToStr(ReceiptRecord.QTY) + ' ' + // 数量
                IntToStr(ReceiptRecord.Weight) + ' ' + // 重量
                IntToStr(ReceiptRecord.UPrice) + ' ' + // 单价
                IntToStr(ReceiptRecord.UpriceAfDisc) + ' ' + // 折扣单价
                IntToStr(ReceiptRecord.UPDiscAmt) + ' ' + // 单价折扣
                IntToStr(ReceiptRecord.NPrice) + ' ' + // 名义总价
                IntToStr(ReceiptRecord.ActPrice) + ' ' + // 实际总价
                IntToStr(ReceiptRecord.SaleDate) + ' ' + // 销售日期 DDMMYY
                IntToStr(ReceiptRecord.SaleTime) + ' ' + // 销售时间 HHMM
                ReceiptRecord.ItemName;

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('销售明细报表.csv'); // 写入
    CsvLines.Free;
    CommaStr.Free;
  end;
end;

procedure TForm1.btnReadSaleTotalClick(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: ReceiptTotal; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '收据编号 交易数量(包括取消的交易) 退货数量 合计重量 ' +
      '合计数量 现金支付 其他支付 券支付金额 信用卡支付 实际收入现金 找零 ' + '折扣金额 销售日期(DDMMYY) 销售时间(HHMM)';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收交易合计 函数  function ReadReceiptTotal(s: TSocket; ReceiptData: PReceiptTotal; RecordNo: Integer): Integer
        // s : socket句柄; PReceiptTotal: 销售合计数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadReceiptTotal(s, RecNo, @ReceiptRecord); // 回收交合计数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo) + ' ' +
              // 收据编号
              IntToStr(ReceiptRecord.NTrans) + ' ' + // 交易数量(包括取消的交易)
                IntToStr(ReceiptRecord.RefundTrans) + ' ' + // 退货数量
                IntToStr(ReceiptRecord.Weight) + ' ' + // 合计重量
                IntToStr(ReceiptRecord.QTY) + ' ' + // 合计数量
                IntToStr(ReceiptRecord.CashRecv) + ' ' + // 现金支付
                IntToStr(ReceiptRecord.ValOfCheques) + ' ' + // 其他支付
                IntToStr(ReceiptRecord.ValOfVoucher) + ' ' + // 券支付金额
                IntToStr(ReceiptRecord.ValOfCredit) + ' ' + // 信用卡支付
                IntToStr(ReceiptRecord.TenderAmt) + ' ' + // 实际收入现金
                IntToStr(ReceiptRecord.ChangeAmt) + ' ' + // 找零
                IntToStr(ReceiptRecord.DiscAmt) + ' ' + // 折扣金额
                IntToStr(ReceiptRecord.SaleDate) + ' ' + // 销售日期 DDMMYY
                IntToStr(ReceiptRecord.SaleTime); // 销售时间 HHMM

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('销售合计报表.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnReadStockClick(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: StockRecord; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '记录编号 秤号 PLU号 数量或重量 票据号 类型(0-计重;1-计件) ' +
      '库存操作类型(0-订货;1-收货;2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗]) ' +
      '保存状态[0-正常记录;1-临时记录]) 退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他) ' +
      '收货流水号 日期(DDMMYY) 时间(HHMM)';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadStock(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo) + ' ' +
              // 记录编号
              IntToStr(ReceiptRecord.ScaleNo) + ' ' + // 秤号
                IntToStr(ReceiptRecord.PLUNo) + ' ' + // PLU号
                IntToStr(ReceiptRecord.WeightOrQTY) + ' ' + // 数量或重量
                IntToStr(ReceiptRecord.ListNo) + ' ' + // 票据号
                IntToStr(ReceiptRecord.ItemType) + ' ' + // 类型(0-计重;1-计件)
                IntToStr(ReceiptRecord.StockType) + ' ' + // 库存操作类型(0-订货;1-收货 2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗])
                IntToStr(ReceiptRecord.Status) + ' ' + // 保存状态[0-正常记录;1-临时记录])
                IntToStr(ReceiptRecord.RtReason) + ' ' +
              // 退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他)
              ReceiptRecord.InboundNo + ' ' + // 收货流水号
                IntToStr(ReceiptRecord.Date) + ' ' + // 日期 DDMMYY
                IntToStr(ReceiptRecord.Time); // 时间 HHMM

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('库存操作报表.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnDelSaleDetailClick(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  RecNo: Integer;
  i, iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    CsvLines.LoadFromFile('销售明细报表.csv'); // 写入

    if (s > 0) then // 连接成功
    begin
      for i := 1 to CsvLines.Count - 1 do
      begin
        CommaStr.Delimiter := ',';
        CommaStr.DelimitedText := CsvLines[i];
        RecNo := StrToInt(CommaStr[0]);
        // 删除交易明细 函数 function DelReceiptDetailRD(s: TSocket; RecordNo: Integer): Integer
        // s : socket句柄; RecordNo : 请求的记录编号(从0开始)
        CommResult := DelReceiptDetailRD(s, RecNo); // 回收交易明细数据
        case CommResult of
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue;
            end;
          $E1:
            Break; // 删除出错
          $E2:
            begin
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue; // 没有此数据
            end;
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnDelSaleTotalClick(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  RecNo: Integer;
  i, iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    CsvLines.LoadFromFile('销售合计报表.csv'); // 写入

    if (s > 0) then // 连接成功
    begin
      for i := 1 to CsvLines.Count - 1 do
      begin
        CommaStr.Delimiter := ',';
        CommaStr.DelimitedText := CsvLines[i];
        RecNo := StrToInt(CommaStr[0]);
        // 删除交易合计 函数 function DelReceiptDetailRD(s: TSocket; RecordNo: Integer): Integer
        // s : socket句柄; RecordNo : 请求的记录编号(从0开始)
        CommResult := DelReceiptTotalRD(s, RecNo); // 回收交易明细数据
        case CommResult of
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue;
            end;
          $E1:
            Break; // 删除出错
          $E2:
            begin
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue; // 没有此数据
            end;
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnDelStockClick(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  RecNo: Integer;
  i, iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    CsvLines.LoadFromFile('库存操作报表.csv'); // 写入

    if (s > 0) then // 连接成功
    begin
      for i := 1 to CsvLines.Count - 1 do
      begin
        CommaStr.Delimiter := ',';
        CommaStr.DelimitedText := CsvLines[i];
        RecNo := StrToInt(CommaStr[0]);
        // 删除库存操作 函数 function DelStock(s: TSocket; RecordNo: Integer): Integer
        // s : socket句柄; RecordNo : 请求的记录编号(从0开始)
        CommResult := DelStockRD(s, RecNo); // 回收交易明细数据
        case CommResult of
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue;
            end;
          $E1:
            Break; // 删除出错
          $E2:
            begin
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue; // 没有此数据
            end;
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnPLUTTLTraceClick(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: Plu_T_Transaction; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '记录编号 PLU号 单价 重量 数量 ' +
      '总价 销售日期(DDMMYY) 销售时间(HHMM) 品名';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 收交易明细 函数  function ReadReceiptDetail(s: TSocket; ReceiptData: PReceiptDetail; RecordNo: Integer): Integer
        // s : socket句柄; PReceiptDetail: 销售明细数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadPLUTTLTrans(s, RecNo, @ReceiptRecord); // 回收交易明细数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo) + ' ' +
              // 记录编号
              IntToStr(ReceiptRecord.PLUNo) + ' ' + // PLU 编号
                IntToStr(ReceiptRecord.UPrice) + ' ' + // 单价
                IntToStr(ReceiptRecord.Weight) + ' ' + // 重量
                IntToStr(ReceiptRecord.QTY) + ' ' + // 数量
                IntToStr(ReceiptRecord.TPrice) + ' ' + // 总价
                IntToStr(ReceiptRecord.Date) + ' ' + // 销售日期 DDMMYY
                IntToStr(ReceiptRecord.Time) + ' ' + // 销售时间 HHMM
                ReceiptRecord.ItemName; // 品名

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('标签打印信息报表.csv'); // 写入
    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn1Click(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  RecNo: Integer;
  i, iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    CsvLines.LoadFromFile('标签打印信息报表.csv'); // 写入

    if (s > 0) then // 连接成功
    begin
      for i := 1 to CsvLines.Count - 1 do
      begin
        CommaStr.Delimiter := ',';
        CommaStr.DelimitedText := CsvLines[i];
        RecNo := StrToInt(CommaStr[0]);
        // 删除库存操作 函数 function DelStock(s: TSocket; RecordNo: Integer): Integer
        // s : socket句柄; RecordNo : 请求的记录编号(从0开始)
        CommResult := DelPLUTTLTrans(s, RecNo); // 回收交易明细数据
        case CommResult of
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue;
            end;
          $E1:
            Break; // 删除出错
          $E2:
            begin
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;

              Continue; // 没有此数据
            end;
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn2Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: ReceiptPLU; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '记录编号 秤号 PLU号 数量或重量 票据号 类型(0-计重;1-计件) ' +
      '库存操作类型(0-订货;1-收货;2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗]) ' +
      '保存状态[0-正常记录;1-临时记录]) 退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他) ' +
      '收货流水号 日期(DDMMYY) 时间(HHMM)';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := RDPluFAsReceipt(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.PLUNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.PLUNo) + ' ' +
              // 记录编号
              FloatToStr(ReceiptRecord.UPrice / 100) + ' ' + // 单价
                IntToStr(ReceiptRecord.UPriceOverride) + ' ' + // 单价
                ReceiptRecord.Commodity;
              // IntToStr(ReceiptRecord.ScaleNo) + ' ' + //秤号
              // IntToStr(ReceiptRecord.PluNo); // + ' ' + //PLU号
              // IntToStr(ReceiptRecord.WeightOrQTY) + ' ' + //数量或重量
              // IntToStr(ReceiptRecord.ListNo) + ' ' + //票据号
              // IntToStr(ReceiptRecord.ItemType) + ' ' + //类型(0-计重;1-计件)
              // IntToStr(ReceiptRecord.StockType) + ' ' + //库存操作类型(0-订货;1-收货 2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗])
              // IntToStr(ReceiptRecord.Status) + ' ' + //保存状态[0-正常记录;1-临时记录])
              // IntToStr(ReceiptRecord.RtReason) + ' ' + //退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他)
              // ReceiptRecord.InboundNo + ' ' + //收货流水号
              // IntToStr(ReceiptRecord.Date) + ' ' + //日期 DDMMYY
              // IntToStr(ReceiptRecord.Time); //时间 HHMM

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('收据PLU信息.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn3Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: LabelPLU; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := '记录编号 秤号 PLU号 数量或重量 票据号 类型(0-计重;1-计件) ' +
      '库存操作类型(0-订货;1-收货;2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗]) ' +
      '保存状态[0-正常记录;1-临时记录]) 退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他) ' +
      '收货流水号 日期(DDMMYY) 时间(HHMM)';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := RDPluFAsLabel(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.PLUNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.PLUNo) + ' ' +
              // 记录编号
              FloatToStr(ReceiptRecord.UPrice / 100) + ' ' + // 单价
                IntToStr(ReceiptRecord.UPriceOverride) + ' ' + // 单价
                ReceiptRecord.EAN_Data.ItemCode + ' ' + // 品名
                ReceiptRecord.Commodity[0].TxtValue + ' ' + // 品名
                ReceiptRecord.Ingredient[0].TxtValue;
              // IntToStr(ReceiptRecord.ScaleNo) + ' ' + //秤号
              // IntToStr(ReceiptRecord.PluNo); // + ' ' + //PLU号
              // IntToStr(ReceiptRecord.WeightOrQTY) + ' ' + //数量或重量
              // IntToStr(ReceiptRecord.ListNo) + ' ' + //票据号
              // IntToStr(ReceiptRecord.ItemType) + ' ' + //类型(0-计重;1-计件)
              // IntToStr(ReceiptRecord.StockType) + ' ' + //库存操作类型(0-订货;1-收货 2-退仓;3-调拨;4-盘点[整箱];5-盘点[散装];6-盘点[格斗])
              // IntToStr(ReceiptRecord.Status) + ' ' + //保存状态[0-正常记录;1-临时记录])
              // IntToStr(ReceiptRecord.RtReason) + ' ' + //退仓理由(1-滞销;2-质量;3-客退;4-垃圾;5-其他)
              // ReceiptRecord.InboundNo + ' ' + //收货流水号
              // IntToStr(ReceiptRecord.Date) + ' ' + //日期 DDMMYY
              // IntToStr(ReceiptRecord.Time); //时间 HHMM

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('标签PLU信息.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btnSyncDTClick(Sender: TObject);
var
  s: TSocket; //
  Records: ReceiptPLU;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      CommResult := SyncDateTime(s);

      case CommResult of // 判断返回值
        $06:
          begin
            // 计数器加1
            Inc(iCount);
            edt1.Text := IntToStr(iCount);
            Application.ProcessMessages;
          end;
        $E1:
          edt1.Text := '写入出错'; // 写入出错
        $E2:
          edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
      else
        edt1.Text := '未定义的错误'; // 未定义的错误
      end;

    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.FormCreate(Sender: TObject);
begin
//  jvpdrsScaleIP.SetFocus;
end;

procedure TForm1.btn4Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: ScaleInfo; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  version: string;
begin
  s := 0;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收交易合计 函数  function ReadReceiptTotal(s: TSocket; ReceiptData: PReceiptTotal; RecordNo: Integer): Integer
        // s : socket句柄; PReceiptTotal: 销售合计数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadScaleData(s, RecNo, @ReceiptRecord); // 回收交合计数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;

              version := StrPas(ReceiptRecord.Version_Num_Main);

              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn5Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: SMPwd; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  version: string;
begin
  s := 0;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收交易合计 函数  function ReadReceiptTotal(s: TSocket; ReceiptData: PReceiptTotal; RecordNo: Integer): Integer
        // s : socket句柄; PReceiptTotal: 销售合计数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadPassword(s, RecNo, @ReceiptRecord); // 回收交合计数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn6Click(Sender: TObject);
var
  s: TSocket; //
  Records: SMPwd;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;

      ZeroMemory(@Records, SizeOf(Records));
      Records.RecordNo := 1; // PLU 编号
      Records.XMode := 1234; // 商品属性 0-计重 1-计件
      Records.PMode := 3456; // 单价覆盖 0-禁制  1-允许

      // 得到PLU数据 下传PLu数据
      CommResult := SendPassword(s, @Records);
      case CommResult of // 判断返回值
        $06:
          begin
            // 计数器加1
            Inc(iCount);
            edt1.Text := IntToStr(iCount);
            Application.ProcessMessages;
          end;
        $E1:
          edt1.Text := '写入出错'; // 写入出错
        $E2:
          edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
      else
        edt1.Text := '未定义的错误'; // 未定义的错误
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn7Click(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  IPAddress: TStrings;
  iCount: Integer;
begin
  s := 0;

  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      CommResult := DelPassword(s); // 删除秤密码
      case CommResult of
        $06:
          begin
            // 计数器加1
            Inc(iCount);
            edt1.Text := IntToStr(iCount);
            Application.ProcessMessages;
          end;
        $E1:
          edt1.Text := '删除出错'; // 删除出错
      else
        edt1.Text := '未定义的错误'; // 未定义的错误
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;
end;

procedure TForm1.btn8Click(Sender: TObject);
var
  s: TSocket; //
  Records: PSKey;
  i, j: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 2 do
      begin
        for j := 0 to 30 do
        begin
          ZeroMemory(@Records, SizeOf(Records));
          Records.RecordNo := 1; // PLU 编号
          Records.PageNo := i; // 商品属性 0-计重 1-计件
          Records.KeyNo := j; // 单价覆盖 0-禁制  1-允许
          Records.KeySwitchNo := 30 * i + j;
          Records.Status := 0;
          Records.PSKName := '1234';

          // 得到PLU数据 下传PLu数据
          CommResult := SendPresetKey(s, @Records);
          case CommResult of // 判断返回值
            $06:
              begin
                // 计数器加1
                Inc(iCount);
                edt1.Text := IntToStr(iCount);
                Application.ProcessMessages;
              end;
            $E1:
              edt1.Text := '写入出错'; // 写入出错
            $E2:
              edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
          else
            edt1.Text := '未定义的错误'; // 未定义的错误
          end;
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn9Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: PSKey; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := ' ';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadPresetKey(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo)
                + ' ' + IntToStr(ReceiptRecord.PageNo) + ' ' + // 记录编号
                IntToStr(ReceiptRecord.KeyNo) + ' ' + // 单价
                IntToStr(ReceiptRecord.KeySwitchNo)
                + ' ' + ReceiptRecord.PSKName; // 品名

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('预设键文件.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn10Click(Sender: TObject);
var
  s: TSocket; //
  CommResult: Integer;
  IPAddress: TStrings;
  iCount: Integer;
begin
  s := 0;

  edt1.Text := '0';
  try
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      CommResult := DelPSKFile(s); // 删除秤密码
      case CommResult of
        $06:
          begin
            // 计数器加1
            Inc(iCount);
            edt1.Text := IntToStr(iCount);
            Application.ProcessMessages;
          end;
        $E1:
          edt1.Text := '删除出错'; // 删除出错
      else
        edt1.Text := '未定义的错误'; // 未定义的错误
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn11Click(Sender: TObject);
var
  s: TSocket; //
  Records: Trace;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 2 do
      begin

        ZeroMemory(@Records, SizeOf(Records));
        Records.RecordNo := i + 1; // PLU 编号
        // Records.Born := i; //商品属性 0-计重 1-计件
        // Records.Fatten :=8;
        Records.ReferenceCode := '12345678cbsed'; // 单价覆盖 0-禁制  1-允许
        Records.ReferenceType := 1;
        Records.Weight := 12345;
        Records.Gtin := '12344';
        Records.LotNum := '1234';
        Records.SupplierCode := '上海';

        // 得到PLU数据 下传PLu数据
        CommResult := SendTraceability(s, @Records);
        case CommResult of // 判断返回值
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
            end;
          $E1:
            edt1.Text := '写入出错'; // 写入出错
          $E2:
            edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
        else
          edt1.Text := '未定义的错误'; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn12Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: Trace; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := ' ';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadTraceability(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo)
                + ' ' + ReceiptRecord.ReferenceCode + ' ' + // 记录编号
                ReceiptRecord.Gtin + ' ' + // 单价
                ReceiptRecord.SupplierCode + ' ' + ReceiptRecord.SupplierName;
              // 品名

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('追溯码文件.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn15Click(Sender: TObject);
var
  s: TSocket; //
  Records: ShpName;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 31 do
      begin

        ZeroMemory(@Records, SizeOf(Records));
        Records.RecordNo := i + 1; // PLU 编号
        Records.labformat := 17;
        Records.Name[0].TwsFontSize := 21; // shopname line1 font
        Records.Name[0].TxtValue := '生产厂商: 1234'; // shopname  line1
        Records.Name[1].TwsFontSize := 21;
        Records.Name[1].TxtValue := '地址: 1234';
        Records.Name[2].TwsFontSize := 21;
        Records.Name[2].TxtValue := '电话: 1234';

        // 得到PLU数据 下传PLu数据
        CommResult := SendShopName(s, @Records);
        case CommResult of // 判断返回值
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
            end;
          $E1:
            edt1.Text := '写入出错'; // 写入出错
          $E2:
            edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
        else
          edt1.Text := '未定义的错误'; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;
end;

procedure TForm1.btn16Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: ShpName; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := ' ';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadShopName(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo)
                + ' ' + IntToStr(ReceiptRecord.Name[0].TwsFontSize)
                + ' ' + ReceiptRecord.Name[0].TxtValue + ' ' + IntToStr
                (ReceiptRecord.Name[1].TwsFontSize) + ' ' + ReceiptRecord.Name
                [1].TxtValue + ' ' + IntToStr
                (ReceiptRecord.Name[2].TwsFontSize) + ' ' + ReceiptRecord.Name
                [2].TxtValue;
              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('店名文件.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;

end;

procedure TForm1.btn17Click(Sender: TObject);
var
  s: TSocket; //
  Records: FlexiBar;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 3 do
      begin

        ZeroMemory(@Records, SizeOf(Records));
        Records.RecordNo := i + 1; // PLU 编号
        Records.FlagType := 1; // (0 – F2 ;  1 – F1F2;  2 - NO FLAG)
        Records.ItemDigit := 6;
        Records.PData1Digit := 5;
        Records.PData2Digit := 6;
        Records.MidChkDigit := 0;
        Records.LastChkDigit := 1;
        Records.BarcodeType := 0;
        Records.PData1 := 1;
        Records.PData2 := 1;

        // 得到PLU数据 下传PLu数据
        CommResult := SendFlexiBarcode(s, @Records);
        case CommResult of // 判断返回值
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
            end;
          $E1:
            edt1.Text := '写入出错'; // 写入出错
          $E2:
            edt1.Text := '没有足够的内存空间'; // 没有足够的内存空间
        else
          edt1.Text := '未定义的错误'; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

procedure TForm1.btn19Click(Sender: TObject);
var
  s: TSocket; //
  ReceiptRecord: SalesDaily; //
  RecNo: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
  CsvLines, CommaStr: TStringList;
begin
  s := 0;
  // 创建保存文件
  CsvLines := TStringList.Create;
  CommaStr := TStringList.Create;
  edt1.Text := '0';
  try
    RecNo := 0;
    iCount := 0;

    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;

    // 写入Tital
    CommaStr.CommaText := ' ';

    CsvLines.Add(CommaStr.CommaText);
    if (s > 0) then // 连接成功
    begin
      while True do
      begin
        ZeroMemory(@ReceiptRecord, SizeOf(ReceiptRecord));
        // 回收库存操作 函数  function ReadStock(s: TSocket; ReceiptData: PStockRecord; RecordNo: Integer): Integer
        // s : socket句柄; PStockRecord: 库存数据结构的指针; RecNo : 请求的记录编号(从0开始)
        CommResult := ReadSalesDaily(s, RecNo, @ReceiptRecord); // 回收库存操作数据
        case CommResult of
          $0:
            begin
              // 计数器加1
              Inc(iCount);
              // 保存到数据库
              RecNo := ReceiptRecord.RecordNo;
              CommaStr.CommaText := IntToStr(ReceiptRecord.RecordNo) + ' ' +
                IntToStr(ReceiptRecord.GTotal) + ' ' + // 记录编号
                IntToStr(ReceiptRecord.GQTY) + ' ' + // 单价
                IntToStr(ReceiptRecord.GWeight) + ' ' +
                IntToStr(ReceiptRecord.GAmount) + ' ' +
                IntToStr(ReceiptRecord.CorrectCNT) + ' ' +
                IntToStr(ReceiptRecord.CorrectAMT) + ' ' +
                IntToStr(ReceiptRecord.NetAMT) + ' ' +
                IntToStr(ReceiptRecord.CustomerCNT) + ' ' +
                IntToStr(ReceiptRecord.CashCNT) + ' ' +
                IntToStr(ReceiptRecord.CashAMT) + ' ' +
                IntToStr(ReceiptRecord.PaidOutCNT) + ' ' +
                IntToStr(ReceiptRecord.PaidOutAMT) + ' ' +
                IntToStr(ReceiptRecord.CashInDrawerAMT) + ' ' +
                IntToStr(ReceiptRecord.ReceiptNo);

              CsvLines.Add(CommaStr.CommaText);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E0:
            Break; // 读取出错
          $E2:
            Break; // 没有数据
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
    CsvLines.SaveToFile('销售查询日报表.csv'); // 写入

    CsvLines.Free;
    CommaStr.Free;
  end;


end;

procedure TForm1.btn18Click(Sender: TObject);
var
  s: TSocket; //
  Records: LabelPLU;
  i: Integer;
  CommResult: Integer;
  iCount: Integer;
  IPAddress: TStrings;
begin
  s := 0;
  edt1.Text := '0';
  try
    IPAddress := TStringList.Create;
    IPAddress.Delimiter := '.';
    IPAddress.DelimitedText := jvpdrsScaleIP.Text;
    // 连接秤函数   function ConnectScale(scaleIP: string; PortNo: Integer)
    // IP地址 192.9.100.203  端口: 2000+电子秤IP 最后一段
    s := ConnectScale(IPAddress.DelimitedText, 2000 + StrToInt(IPAddress[3]));
    // 创建 socket 连接
    IPAddress.Free;
    if (s > 0) then // 连接成功
    begin
      iCount := 0;
      for i := 0 to 1 do
      begin
        // 准备PLU数据
        ZeroMemory(@Records, SizeOf(Records));
        Records.PLUNo := i + 1; // PLU 编号
        Records.WeightUnit := 0; // 商品属性 0-计重 1-计件
        Records.UPriceOverride := 0; // 单价覆盖 0-禁制  1-允许
        Records.UPrice := 1223; // 单价
        Records.MG := 997; // 小分类编号
        Records.Cost := 678; // 成本价
        Records.LabelFormat1 := 17; //自定义标签格式1
        Records.BarFormat := 5; //条码格式编号
        Records.EANFlags := 20; //店内码 F1F2的值
        Records.EAN_Data.ItemCode := '0022200000'; //商品货号
        Records.EAN_Data.BarCodeType := 0; //条码类型0--EAN; 9--ITF
        Records.EAN_Data.BarRightData := 0;  //条码右侧数据类型('X'类型) 0--金额,1--重量,2--重量

        Records.MBarcode1.MutilBarFunc :=86; // EAN-128码
        Records.MBarcode1.MutilBarData :='F (94)XXXXXXXXXXXXXXXXXXXXXXXXXXCD'; //EAN-128C (94)
        Records.MBarcode1.MutilBarType :=2; //EAN-128码类型 0-A 1-B 2-C
        Records.Commodity[0].TwsFontSize := 21;    //品名字体(第一行)
        Records.Commodity[0].TxtValue := '商品名称abc';//品名(第一行)

        // 得到PLU数据 下传PLu数据
        CommResult := SendLPlu(s, @Records);
        case CommResult of // 判断返回值
          $06:
            begin
              // 计数器加1
              Inc(iCount);
              edt1.Text := IntToStr(iCount);
              Application.ProcessMessages;
              Continue;
            end;
          $E1:
            Continue; // 写入出错
          $E2:
            Break; // 没有足够的内存空间
        else
          Break; // 未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then
      CloseConnect(s); // 关闭与电子秤的连接 函数   function CloseConnect(s: TSocket): Integer
  end;

end;

end.

