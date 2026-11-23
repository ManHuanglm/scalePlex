unit PLU;

interface
uses
  Classes;

type

  SMDiscount = record
    //DiscountNo: Integer;
    StartDate: Integer;
    StartTime: Integer;
    EndDate: Integer;
    EndTime: Integer;
    FirstLIM: Integer;
    Within1stLIM: Integer;
    SecondLIM: Integer;
    Within2ndLIM: Integer;
    DisType: Integer;
    MarkDown: Integer;
  end;

  SM_EANData = record
    ItemCode: array[0..9] of char;
    BarCodeType: Integer;
    BarRightData: Integer;
  end;

//SM 电子秤 带字体的文本结构
  SM_ASCII_TXT = record
    TwsFontSize: Integer; //字体
    TxtValue: array[0..99] of char; //文本内容
  end;


  MutilBarcode = record
    MutilBarFunc: Integer;
    MutilBarData: array[0..48] of Char;
    MutilBarType: Integer;
  end;


  LabelPLU = record
    PLUNo: Integer; //PLU编号
    WeightUnit: Integer; //商品属性(0--计重;1--计件)
    UPriceOverride: Integer; //单价覆盖(0--禁止;1--允许)
    UPrice: Integer; //单价
    LabelFormat1: Integer; //标签格式
    LabelFormat2: Integer; //标签格式
    BarFormat: Integer; //条码格式
    EANFlags: integer; //店内码
    EAN_Data: SM_EANData; //EAN数据
    MG: Integer; //主组(小分类)
    SBDate: Integer; //售出日期
    UBDate: Integer; //保质期
    Cost: Integer; //成本
    Tare: Integer; //皮重
    QTY: Integer; //预置数量
    TaxNo: integer;
    Traceability: integer;
    TraceabilityL: integer;
    MBarcode1: MutilBarcode; //多重条码1
    Discount: SMDiscount; //打折信息
    Commodity: array[0..3] of SM_ASCII_TXT; //最大 4 行,每行100字符
    Ingredient: array[0..14] of SM_ASCII_TXT; //最大 15 行,每行100字符
    SPMessage: array[0..29] of SM_ASCII_TXT; //最大 30 行,每行100字符
  end;

  PLabelPLU = ^LabelPLU;


  ReceiptPLU = record
    PLUNo: Integer; //PLU编号
    WeightUnit: Integer; //商品属性(0--计重;1--计件)
    UPriceOverride: Integer; //单价覆盖(0--禁止;1--允许)
    UPrice: Integer; //单价
    MG: Integer; //主组(小分类)
    Cost: Integer; //成本
    Tare: Integer; //皮重
    QTY: Integer; //预置数量
    CustomDisc: Integer; //会员价
    Discount: SMDiscount; //打折信息
    Commodity: array[0..99] of Char; //商品名称
    BCC: Integer;
  end;
  PReceiptPLU = ^ReceiptPLU;

implementation


end.

