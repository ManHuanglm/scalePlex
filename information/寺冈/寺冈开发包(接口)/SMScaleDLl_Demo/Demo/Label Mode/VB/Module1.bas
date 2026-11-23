Attribute VB_Name = "mdlPLU"

Option Explicit


Public Type SM_EANData
     ItemCode(0 To 9) As Byte '项目代码  十位
     BarCodeType As Long   '条码类型 0-EAN ;9-ITF
     BarRightData As Long  '右侧数据
End Type

Public Type SMDiscount
     StartDate As Long  '
     StartTime As Long  '
     EndDate As Long    '
     EndTime As Long    '
     FirstLIM As Long   '
     Within1stLIM As Long  '
     SecondLIM As Long     '
     Within2ndLIM As Long  '
     DisType As Long       '
     MarkDown As Long      '
End Type


Public Type SM_ASCII_TXT
     TwsFontSize As Long  '字体
     TxtValue(0 To 99) As Byte '文本内容
End Type


Public Type LabelPLU
     PLUNo As Long   'PLU编号
     WeightUnit As Long  '商品属性(0--计重;1--计件)
     UPriceOverride As Long  '单价覆盖(0--禁止;1--允许)
     UPrice As Long  '单价
     LabelFormat1 As Long '标签格式
     BarFormat As Long  '条码格式
     EANFlags As Long  '店内码
     EAN_Data As SM_EANData 'EAN数据
     MG As Long   '主组(小分类)
     SBDate As Long   '售出日期
     UBDate As Long  '保质期
     Cost As Long  '成本
     Tare As Long  '皮重
     QTY As Long  '预置数量
     Discount As SMDiscount  '打折信息
     Commodity(0 To 3) As SM_ASCII_TXT  '最大 4 行,每行100字符
     Ingredient(0 To 14) As SM_ASCII_TXT '最大 15 行,每行100字符
     SPMessage(0 To 29) As SM_ASCII_TXT  '最大 30 行,每行100字符
End Type


Public Declare Function Connect Lib "SMScaleAPI.dll" Alias "ConnectScale" (ByVal ScaleIP As String, ByVal PortNo As Long) As Long
Public Declare Function SendPlu Lib "SMScaleAPI.dll" Alias "SendPluForLabel" (ByVal s As Long, ByVal PLUData As Long) As Long

Public Declare Function Disconnect Lib "SMScaleAPI.dll" Alias "CloseConnect" (ByVal s As Long) As Long


