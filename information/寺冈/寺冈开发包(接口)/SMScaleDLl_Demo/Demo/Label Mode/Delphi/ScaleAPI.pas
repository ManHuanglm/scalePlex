unit ScaleAPI;

interface
uses
  WinSock,PLU,
  SalesReceipt,Stock,
  ScaleData,Password,
  PreSetKey,Traceability,
  ShopName,FlexiBarcode,
  SalesTotal;

function ConnectScale(scaleIP: string; PortNo: Integer): TSocket; stdcall; external 'SMScaleAPI.dll';
function CloseConnect(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//PLU文件
function SendRPlu(s: TSocket; PLUData: PReceiptPLU): Integer; stdcall; external 'SMScaleAPI.dll';
function SendLPlu(s: TSocket; PLUData: PLabelPLU): Integer; stdcall; external 'SMScaleAPI.dll';
function RDPluFAsReceipt(s: TSocket; RecordNo: Integer; RPLUData: PReceiptPLU): Integer; stdcall; external 'SMScaleAPI.dll';
function RDPluFAsLabel(s: TSocket; RecordNo: Integer; LPLUData: PLabelPLU): Integer; stdcall; external 'SMScaleAPI.dll';
//销售明细
function ReadReceiptDetail(s: TSocket; RecordNo: Integer;ReceiptData: PReceiptDetail): Integer; stdcall; external 'SMScaleAPI.dll';
function DelReceiptDetailRD(s: TSocket; RecordNo: Integer): Integer; stdcall; external 'SMScaleAPI.dll';
//销售合计
function ReadReceiptTotal(s: TSocket; RecordNo: Integer; ReceiptData: PReceiptTotal): Integer; stdcall; external 'SMScaleAPI.dll';
function DelReceiptTotalRD(s: TSocket; RecordNo: Integer): Integer; stdcall; external 'SMScaleAPI.dll';
//标签交易
function ReadPLUTTLTrans(s: TSocket; RecordNo: Integer;PLUTTLTrans: PPlu_T_Transaction): Integer; stdcall; external 'SMScaleAPI.dll';
function DelPLUTTLTrans(s: TSocket; RecordNo: Integer): Integer; stdcall; external 'SMScaleAPI.dll';
//库存操作文件
function ReadStock(s: TSocket; RecordNo: Integer; StockOpt: PStockRecord): Integer; stdcall; external 'SMScaleAPI.dll';
function DelStockRD(s: TSocket; RecordNo: Integer): Integer; stdcall; external 'SMScaleAPI.dll';
//同步时间
function SyncDateTime(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//读秤信息
function ReadScaleData(s: TSocket; RecordNo: Integer; ScaleInfo: PScaleInfo): Integer; stdcall; external 'SMScaleAPI.dll';
//密码文件
function ReadPassword(s: TSocket; RecordNo: Integer; ppwd: PSMPwd): Integer; stdcall; external 'SMScaleAPI.dll';
function SendPassword(s: TSocket; ppwd: PSMPwd): Integer; stdcall; external 'SMScaleAPI.dll';
function DelPassword(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//预设键
function ReadPreSetKey(s: TSocket; RecordNo: Integer; ppsk: PPSKey): Integer; stdcall; external 'SMScaleAPI.dll';
function SendPresetKey(s: TSocket; ppsk: PPSKey): Integer; stdcall; external 'SMScaleAPI.dll';
function DelPSKFile(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//追溯码
function ReadTraceability(s: TSocket; RecordNo: Integer; pTraceability: PTrace): Integer; stdcall; external 'SMScaleAPI.dll';
function SendTraceability(s: TSocket; pTraceability: PTrace): Integer; stdcall; external 'SMScaleAPI.dll';
//function DelPSKFile(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//店名
function ReadShopName(s: TSocket; RecordNo: Integer; pshopname: PShpName): Integer; stdcall; external 'SMScaleAPI.dll';
function SendShopName(s: TSocket; pshopname: PShpName): Integer; stdcall; external 'SMScaleAPI.dll';
function DelSHPFile(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//自定义条码
function ReadFlexiBarcode(s: TSocket; RecordNo: Integer; pFlxBar: PFlexiBar): Integer; stdcall; external 'SMScaleAPI.dll';
function SendFlexiBarcode(s: TSocket; pFlxBar: PFlexiBar): Integer; stdcall; external 'SMScaleAPI.dll';
//function DelSHPFile(s: TSocket): Integer; stdcall; external 'SMScaleAPI.dll';
//店铺日报表
function ReadSalesDaily(s: TSocket; RecordNo: Integer; pSalesD: PSalesDaily): Integer; stdcall; external 'SMScaleAPI.dll';

implementation

end.

 