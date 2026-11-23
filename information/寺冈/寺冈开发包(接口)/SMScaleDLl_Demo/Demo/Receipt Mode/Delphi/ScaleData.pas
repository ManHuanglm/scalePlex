unit ScaleData;

interface
uses
  Classes;

type
  ScaleInfo = record
    RecordNo: Integer;
    RecSize: Integer;
    Dept: Integer;
    Rct_Shop: Integer; //
    Lbl_Shop: Integer;
    Preset_Key: Integer;
    Scale_Code: Integer;
    I_Lbl_Format: Integer;
    T_Lbl_Format: Integer;
    I_Bar_Barcode: Integer;
    I_Bar_R_Data: Integer;
    I_Bar_R_Price_Data: Integer;
    I_Bar_Flag: Integer;
    T_Barcode: Integer;
    T_Bar_L_Data: Integer;
    T_Bar_Fix_L_Data: array[0..9] of Char;
    T_Bar_Flag: Integer;
    T_Bar_R_Data: Integer;
    T_Bar_Prt_Rct: Integer;
    Prt_Bar_F1: Integer;
    Last_Act_Date: array[0..15] of Char;
    Plu_Call_Low: Integer;
    Plu_Call_Upper: Integer;
    Apc_Date: Integer;
    Ethernet_Addr: array[0..11] of Char;
    Ip_Addr: array[0..11] of Char;
    Thermal_Hd_Usage_Ctr: Integer;
    Version_Num_Main: array[0..3] of Char;
    Number_Plu: Integer;
    Ram_Size: Integer;
    Preset_Group: Integer;
    Rct_Sp: Integer;
    Program_Update_Chk: Integer;
    Mainboard_Type: Integer;
    Plu_Remain: Integer;
    Data_Update_Date: Integer;
  end;

  PScaleInfo = ^ScaleInfo;

implementation


end.

