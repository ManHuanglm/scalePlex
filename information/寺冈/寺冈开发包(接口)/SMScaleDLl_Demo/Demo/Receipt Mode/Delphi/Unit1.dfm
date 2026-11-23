object Form1: TForm1
  Left = 293
  Top = 141
  Width = 772
  Height = 576
  Caption = 'Form1'
  Color = clBtnFace
  Font.Charset = DEFAULT_CHARSET
  Font.Color = clWindowText
  Font.Height = -11
  Font.Name = 'MS Sans Serif'
  Font.Style = []
  OldCreateOrder = False
  OnCreate = FormCreate
  PixelsPerInch = 96
  TextHeight = 13
  object lbl1: TLabel
    Left = 8
    Top = 16
    Width = 105
    Height = 17
    Alignment = taRightJustify
    AutoSize = False
    Caption = #30005#23376#31204'IP'#22320#22336':'
    Font.Charset = DEFAULT_CHARSET
    Font.Color = clWindowText
    Font.Height = -13
    Font.Name = 'MS Sans Serif'
    Font.Style = []
    ParentFont = False
  end
  object lblStatus: TLabel
    Left = 16
    Top = 56
    Width = 97
    Height = 17
    Alignment = taRightJustify
    AutoSize = False
    Caption = #36827#24230':'
    Font.Charset = DEFAULT_CHARSET
    Font.Color = clWindowText
    Font.Height = -13
    Font.Name = 'MS Sans Serif'
    Font.Style = []
    ParentFont = False
  end
  object lbl2: TLabel
    Left = 280
    Top = 16
    Width = 161
    Height = 17
    Alignment = taRightJustify
    AutoSize = False
    Caption = #38656#35201#25805#20316#30340#35760#24405#32534#21495':'
    Font.Charset = DEFAULT_CHARSET
    Font.Color = clWindowText
    Font.Height = -13
    Font.Name = 'MS Sans Serif'
    Font.Style = []
    ParentFont = False
    Visible = False
  end
  object btnReadSaleDetail: TBitBtn
    Left = 152
    Top = 96
    Width = 130
    Height = 49
    Caption = #22238#25910#38144#21806#26126#32454
    TabOrder = 4
    OnClick = btnReadSaleDetailClick
  end
  object btnReadSaleTotal: TBitBtn
    Left = 152
    Top = 208
    Width = 130
    Height = 49
    Caption = #22238#25910#38144#21806#21512#35745
    TabOrder = 11
    OnClick = btnReadSaleTotalClick
  end
  object btnReadStock: TBitBtn
    Left = 288
    Top = 208
    Width = 130
    Height = 49
    Caption = #22238#25910#24211#23384#25968#25454
    TabOrder = 12
    OnClick = btnReadStockClick
  end
  object btnSendPLU: TButton
    Left = 16
    Top = 96
    Width = 130
    Height = 49
    Caption = #19979#20256'PLU('#25910#25454#25968#25454')'
    TabOrder = 3
    OnClick = btnSendPLUClick
  end
  object edt1: TEdit
    Left = 120
    Top = 56
    Width = 153
    Height = 24
    Font.Charset = DEFAULT_CHARSET
    Font.Color = clWindowText
    Font.Height = -13
    Font.Name = 'MS Sans Serif'
    Font.Style = []
    ParentFont = False
    TabOrder = 2
  end
  object btnDelStock: TBitBtn
    Left = 16
    Top = 544
    Width = 130
    Height = 49
    Caption = #21024#38500#24211#23384#25968#25454
    TabOrder = 25
    OnClick = btnDelStockClick
  end
  object btnDelSaleTotal: TBitBtn
    Left = 152
    Top = 264
    Width = 130
    Height = 49
    Caption = #21024#38500#38144#21806#21512#35745
    TabOrder = 14
    OnClick = btnDelSaleTotalClick
  end
  object btnDelSaleDetail: TBitBtn
    Left = 152
    Top = 152
    Width = 130
    Height = 49
    Caption = #21024#38500#38144#21806#26126#32454
    TabOrder = 8
    OnClick = btnDelSaleDetailClick
  end
  object btnPLUTTLTrace: TBitBtn
    Left = 288
    Top = 96
    Width = 130
    Height = 49
    Caption = #22238#25910#26631#31614#25171#21360#20449#24687
    TabOrder = 5
    OnClick = btnPLUTTLTraceClick
  end
  object btn1: TBitBtn
    Left = 288
    Top = 152
    Width = 130
    Height = 49
    Caption = #21024#38500#26631#31614#25171#21360#20449#24687#25968#25454
    TabOrder = 9
    OnClick = btn1Click
  end
  object btn2: TButton
    Left = 16
    Top = 152
    Width = 130
    Height = 49
    Caption = #35835#21462'PLU('#25910#25454#20449#24687')'
    TabOrder = 7
    OnClick = btn2Click
  end
  object btn3: TButton
    Left = 16
    Top = 264
    Width = 130
    Height = 49
    Caption = #35835#21462'PLU('#26631#31614#20449#24687')'
    TabOrder = 13
    OnClick = btn3Click
  end
  object btnSyncDT: TBitBtn
    Left = 424
    Top = 96
    Width = 130
    Height = 49
    Caption = #26102#38388#21516#27493
    TabOrder = 6
    OnClick = btnSyncDTClick
  end
  object btn5: TBitBtn
    Left = 16
    Top = 376
    Width = 130
    Height = 49
    Caption = #35835#21462#31204#23494#30721
    TabOrder = 19
    OnClick = btn5Click
  end
  object btn6: TBitBtn
    Left = 16
    Top = 320
    Width = 130
    Height = 49
    Caption = #19979#20256#31204#23494#30721
    TabOrder = 16
    OnClick = btn6Click
  end
  object btn7: TBitBtn
    Left = 16
    Top = 432
    Width = 130
    Height = 49
    Caption = #21024#38500#31204#23494#30721
    TabOrder = 22
    OnClick = btn7Click
  end
  object btn8: TBitBtn
    Left = 152
    Top = 320
    Width = 130
    Height = 49
    Caption = #19979#20256#39044#35774#38190
    TabOrder = 17
    OnClick = btn8Click
  end
  object btn9: TBitBtn
    Left = 152
    Top = 376
    Width = 130
    Height = 49
    Caption = #35835#21462#39044#35774#38190
    TabOrder = 20
    OnClick = btn9Click
  end
  object btn10: TBitBtn
    Left = 152
    Top = 432
    Width = 130
    Height = 49
    Caption = #21024#38500#39044#35774#38190#25991#20214
    TabOrder = 23
    OnClick = btn10Click
  end
  object btn11: TBitBtn
    Left = 152
    Top = 544
    Width = 130
    Height = 49
    Caption = #19979#20256#36861#28335#30721#20449#24687
    TabOrder = 26
    OnClick = btn11Click
  end
  object edt2: TEdit
    Left = 447
    Top = 13
    Width = 153
    Height = 24
    Font.Charset = DEFAULT_CHARSET
    Font.Color = clWindowText
    Font.Height = -13
    Font.Name = 'MS Sans Serif'
    Font.Style = []
    ParentFont = False
    TabOrder = 0
    Visible = False
  end
  object btn15: TBitBtn
    Left = 288
    Top = 376
    Width = 130
    Height = 49
    Caption = #19979#20256#24215#21517#25991#20214
    TabOrder = 21
    OnClick = btn15Click
  end
  object btn16: TBitBtn
    Left = 288
    Top = 432
    Width = 130
    Height = 49
    Caption = #35835#24215#21517#25991#20214
    TabOrder = 24
    OnClick = btn16Click
  end
  object btn17: TBitBtn
    Left = 288
    Top = 320
    Width = 130
    Height = 49
    Caption = #19979#20256#33258#23450#20041#26465#30721
    TabOrder = 18
    OnClick = btn17Click
  end
  object jvpdrsScaleIP: TJvIPAddress
    Left = 120
    Top = 16
    Width = 154
    Height = 21
    AddressValues.Address = 0
    AddressValues.Value1 = 0
    AddressValues.Value2 = 0
    AddressValues.Value3 = 0
    AddressValues.Value4 = 0
    ParentColor = False
    TabOrder = 1
    Text = '0.0.0.0'
  end
  object btn19: TBitBtn
    Left = 288
    Top = 264
    Width = 130
    Height = 49
    Caption = #35835#24215#38138#26085#25253#34920
    TabOrder = 15
    OnClick = btn19Click
  end
  object btn18: TButton
    Left = 16
    Top = 208
    Width = 130
    Height = 49
    Caption = #19979#20256'PLU('#26631#31614#25968#25454')'
    TabOrder = 10
    OnClick = btn18Click
  end
end
