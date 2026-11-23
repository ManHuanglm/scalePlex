unit ReadData;

interface

uses
  Classes{$IFDEF MSWINDOWS}, Windows{$ENDIF}, WinSock, SysUtils;

type
  TReadData = class(TThread)
  private
    ScaleID: Integer;
  public
    procedure setScaleID(ScaleID: Integer);
    procedure ReadReceiptDetail();
    procedure ReadReceiptTotal();
    procedure ReadStockOperation();

   // constructor create(Scale: TScale; SMFileNo: Integer);
   // destructor Destroy; override;

  protected
    procedure Execute; override;

  end;

implementation
uses
  Scale, SalesReceipt,Stock, ScaleAPI;

procedure TReadData.setScaleID(ScaleID: Integer);
begin
  Self.ScaleID := ScaleID;
end;

procedure TReadData.Execute;
const
  SEMAPHORE_ALL_ACCESS = $1F0003;
var
  SmHandle: THandle;
  hMutex: THandle; //
begin
  SmHandle := OpenSemaphore(SEMAPHORE_ALL_ACCESS, False, 'ReadThreads');
  WaitForSingleObject(SmHandle, INFINITE);
  try
    //线程开始标志
    G_CommMonitor[scaleID].Resume;
    G_ScaleList[scaleID].ScaleThreadStat := 1;
    case G_ScaleList[scaleID].FileNo of
      $51: ReadReceiptDetail; //销售明细
      $52: ReadReceiptTotal; //销售合计
      $A2: ReadStockOperation; //库存操作
    end;
  finally
    //线程结束
    G_ScaleList[scaleID].ScaleThreadStat := 0;
    ReleaseSemaphore(SmHandle, 1, nil);
    if SmHandle<>0 then CloseHandle(SmHandle);
    hMutex :=OpenMutex(MUTEX_ALL_ACCESS,False,PChar('ReadThreadMutex'));
    WaitForSingleObject(hMutex,INFINITE);
    Dec(G_ScaleCommThreadCount);
    ReleaseMutex(hMutex);
    if hMutex<>0 then CloseHandle(hMutex);
  end;
end;

procedure TReadData.ReadReceiptDetail();
var
  s: TSocket; //
  ScaleReceipts: TReceiptDetail; //
  ReceiptRecord: ReceiptDetail;  //
  RecNo: Integer;
begin
  s := 0;
  RecNo := 0;
  ScaleReceipts := TReceiptDetail.create;
  try
    ScaleReceipts.setStoreNo(G_ScaleList[ScaleID].StoreNo);
    ScaleReceipts.setStoreName(G_ScaleList[ScaleID].storeName);
    ScaleReceipts.setScaleNo(G_ScaleList[ScaleID].ScaleNo);

    ZeroMemory(@ReceiptRecord, SizeOf(ReceiptDetail));
    //连接到秤
    s := ConnectScale(PChar(G_ScaleList[ScaleID].InterIP), G_ScaleList[ScaleID].PortNo); //创建 socket 连接
    if (s > 0) then //连接成功
    begin
      while True do begin
         G_ScaleList[ScaleID].CommResult := ScaleAPI.ReadReceiptDetail(s, @ReceiptRecord, RecNo); //回收交易明细数据
        case G_ScaleList[ScaleID].CommResult of
          $0:
            begin
              ScaleReceipts.setRecordData(ReceiptRecord);
              //计数器加1
              Inc(G_ScaleList[ScaleID].CommCount);
              //保存到数据库
              ScaleReceipts.Insert2DB;
              //返回记录编号
              RecNo := ReceiptRecord.RecordNo;
              //
              ZeroMemory(@ReceiptRecord, SizeOf(ReceiptDetail));
              Continue;
            end;
          $E0: Break; //读取出错
          $E2: Break; //没有数据
        else
          Break; //未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then CloseConnect(s);
    if ScaleReceipts <> nil then ScaleReceipts.Free;
  end;
end;

procedure TReadData.ReadReceiptTotal();
var
  s: TSocket; //
  ScaleReceipts: TReceiptTotal; //
  ReceiptRecord: ReceiptTotal;  //
  RecNo: Integer;  //
begin
  s := 0;
  RecNo := 0;
  ScaleReceipts := TReceiptTotal.create;
  try
    ScaleReceipts.setStoreNo(G_ScaleList[ScaleID].StoreNo);
    ScaleReceipts.setStoreName(G_ScaleList[ScaleID].storeName);
    ScaleReceipts.setScaleNo(G_ScaleList[ScaleID].ScaleNo);
    //
    ZeroMemory(@ReceiptRecord, SizeOf(ReceiptTotal));
    //连接到秤
    s := ConnectScale(PChar(G_ScaleList[ScaleID].InterIP), G_ScaleList[ScaleID].PortNo); //创建 socket 连接
    if (s > 0) then //连接成功
    begin
      while True do begin
         G_ScaleList[ScaleID].CommResult := ScaleAPI.ReadReceiptTotal(s, @ReceiptRecord, RecNo); //回收交易明细数据
        case G_ScaleList[ScaleID].CommResult of
          $0:
            begin
              ScaleReceipts.setRecordData(ReceiptRecord);
              //计数器加1
              Inc(G_ScaleList[ScaleID].CommCount);
              //保存到数据库
              ScaleReceipts.Insert2DB;
              //返回记录编号
              RecNo := ReceiptRecord.RecordNo;
              //
              ZeroMemory(@ReceiptRecord, SizeOf(ReceiptDetail));
              Continue;
            end;
          $E0: Break; //读取出错
          $E2: Break; //没有数据
        else
          Break; //未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then CloseConnect(s);
    if ScaleReceipts <> nil then ScaleReceipts.Free;
  end;
end;

procedure TReadData.ReadStockOperation();
var
  s: TSocket; //
  StockdateOpt: TStockOperation; //
  StockData: StockRecord;  //
  RecNo: Integer;  //
begin
  s := 0;
  RecNo := 0;
  StockdateOpt := TStockOperation.create;
  try
    StockdateOpt.setStoreNo(G_ScaleList[ScaleID].StoreNo);
    StockdateOpt.setStoreName(G_ScaleList[ScaleID].storeName);
    StockdateOpt.setScaleNo(G_ScaleList[ScaleID].ScaleNo);
    //
    ZeroMemory(@StockData, SizeOf(StockRecord));

    //连接到秤
    s :=ConnectScale(PChar(G_ScaleList[ScaleID].InterIP), G_ScaleList[ScaleID].PortNo); //创建 socket 连接
    if (s > 0) then //连接成功
    begin
      while True do begin
          G_ScaleList[ScaleID].CommResult :=ScaleAPI.ReadStock(s, @StockData, RecNo); //回收交易明细数据
        case G_ScaleList[ScaleID].CommResult of
          $0:
            begin
              StockdateOpt.setRecordData(StockData);
              //计数器加1
              Inc(G_ScaleList[ScaleID].CommCount);
              //保存到数据库
              StockdateOpt.Insert2DB(StockData);
              //返回记录编号
              RecNo := StockData.RecordNo;
              //
              ZeroMemory(@StockData, SizeOf(StockRecord));
              Continue;
            end;
          $E0: Break; //读取出错
          $E2: Break; //没有数据
        else
          Break; //未定义的错误
        end;
      end;
    end;
  finally
    if s > 0 then CloseConnect(s);
    if StockdateOpt <> nil then StockdateOpt.Free;
  end;
end;

end.

