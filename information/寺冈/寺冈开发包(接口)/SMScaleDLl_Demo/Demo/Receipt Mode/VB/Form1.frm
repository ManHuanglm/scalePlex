VERSION 5.00
Begin VB.Form Form1 
   Caption         =   "Form1"
   ClientHeight    =   4680
   ClientLeft      =   60
   ClientTop       =   450
   ClientWidth     =   7530
   LinkTopic       =   "Form1"
   ScaleHeight     =   4680
   ScaleWidth      =   7530
   StartUpPosition =   3  'Windows Default
   Begin VB.CommandButton Command1 
      Caption         =   "删除PLU文件"
      Height          =   825
      Left            =   3480
      TabIndex        =   2
      Top             =   1890
      Width           =   2085
   End
   Begin VB.CommandButton cmdPLUDownload 
      Caption         =   "下传PLU"
      Height          =   825
      Left            =   630
      TabIndex        =   0
      Top             =   1920
      Width           =   2085
   End
   Begin VB.Label lbStatus 
      Caption         =   "进度:      "
      Height          =   285
      Left            =   480
      TabIndex        =   1
      Top             =   900
      Width           =   3195
   End
End
Attribute VB_Name = "Form1"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False

Private Sub cmdPLUDownload_Click()
    Dim s As Long
    Dim PLUs As LabelPLU
    Dim strtemp() As Byte
   
    Dim commResult As Long
    
    Dim i, j As Integer

    '连接电子秤 IP地址,端口号
    '端口号 : 2000+IP地址的最后一段 例如 2000+203
    s = Connect("192.9.100.203", 2203) '连接电子秤
     If s > 0 Then
        For j = 1 To 100
            PLUs.PLUNo = j
            PLUs.WeightUnit = 0
            PLUs.UPriceOverride = 0
            PLUs.UPrice = 123
            PLUs.LabelFormat1 = 17
            PLUs.BarFormat = 5
            PLUs.EANFlags = 20
            
            strtemp = StrConv("1243500000", vbFromUnicode)
            
            For i = LBound(strtemp) To UBound(strtemp)
                PLUs.EAN_Data.ItemCode(i) = strtemp(i)
            Next i
            
            PLUs.EAN_Data.BarCodeType = 9
            PLUs.EAN_Data.BarRightData = 2
            PLUs.MG = 997
            PLUs.SBDate = 0
            PLUs.UBDate = 10
            
            'For EAN128 Barcode
            PLUs.MBarcode1.MutilBarFunc = 86 'fixed
            strtemp = StrConv("F (94)XXXXXXXXXXXXXXXXXXXXXXXXXXCD", vbFromUnicode) 'fixed
            For i = LBound(strtemp) To UBound(strtemp)
                PLUs.MBarcode1.MutilBarData(i) = strtemp(i)
            Next i
            
            PLUs.MBarcode1.MutilBarType = 2 'fixed
            
            PLUs.Discount.StartDate = 90714
            PLUs.Discount.StartTime = 0
            PLUs.Discount.EndDate = 90715
            PLUs.Discount.EndTime = 715
            PLUs.Discount.FirstLIM = 0
            PLUs.Discount.SecondLIM = 0
            PLUs.Discount.Within1stLIM = 0
            PLUs.Discount.Within2ndLIM = 20
            PLUs.Discount.DisType = 2
            PLUs.Discount.MarkDown = 1
    
            PLUs.Commodity(0).TwsFontSize = 21
            
            strtemp = StrConv("商品名称abc", vbFromUnicode)
            For i = LBound(strtemp) To UBound(strtemp)
                PLUs.Commodity(0).TxtValue(i) = strtemp(i)
            Next i
    
            commResult = SendPlu(s, VarPtr(PLUs))
            If commResult <> 6 Then
                Exit For
            End If
            DoEvents
            lbStatus = j
        Next j
       
    End If
    
    Disconnect (s) '关闭连接
End Sub


Private Sub Command1_Click()

    Dim s As Long
    
    Dim commResult As Long
    

    '连接电子秤 IP地址,端口号
    '端口号 : 2000+IP地址的最后一段 例如 2000+203
    s = Connect("10.109.68.125", 2125) '连接电子秤
    If s > 0 Then
        commResult = DeletePLUFile(s)
        
        If commResult = 6 Then
            MsgBox "Delete PLU file successfully!"
        Else
            MsgBox "Delete PLU file failed!"
        End If
    End If
    Disconnect (s) '关闭连接
End Sub
