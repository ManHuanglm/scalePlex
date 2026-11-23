unit FlexiBarcode;

interface
uses
  Classes;

type
  FlexiBar = record
    RecordNo: Integer; //BARCODE FILE NO             -  4 BYTES  (BCD)
    RecSize: Integer; //BARCODE RECORD SIZE         -  2 BYTES  (HEX)
    FlagType: Integer; //FLAG TYPE                   -  1 BYTE   (BCD) (0 ¨C F2 ;  1 ¨C F1F2;  2 - NO FLAG)
    ItemDigit: Integer; //ITEM CODE DIGIT NO          -  1 BYTE   (1 ¨C 7)
    PData1Digit: integer; //PROGRAM DATA1 DIGIT NO      -  1 BYTE   (BCD, 0 - 9)
    PData2Digit: integer; //PROGRAM DATA2 DIGIT NO      -  1 BYTE   (BCD, 0 - 9)
    MidChkDigit: integer; //Bit0 - Mid.CD , 0: No; 1: Yes
    LastChkDigit: integer; //Bit1 - Last CD.  0: No; 1: Yes
    BarcodeType: integer; //Bit7 - Barcode type,  0: ITF; 1: EAN
    PData1: Integer; //* PROGRAM DATA1               -  1 BYTE (0 - 6)
    PData2: Integer; //* PROGRAM DATA2               -  1 BYTE (0 - 6)
    PData1Shift: Integer; //** PROGRAM DATA1 SHIFT        -  1 BYTE (0 - 5)
    PData2Shift: Integer; //** PROGRAM DATA2 SHIFT        -  1 BYTE (0 - 5)
  end;

   PFlexiBar = ^FlexiBar;
implementation


end.

 