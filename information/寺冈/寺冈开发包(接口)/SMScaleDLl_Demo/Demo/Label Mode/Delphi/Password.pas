unit Password;

interface
uses
  Classes;

type
  SMPwd = record
    RecordNo: Integer;
    RecSize: Integer;
    XMode: Integer; //X MODE PASSWORD        -  3 BYTES
    SMode: Integer; //S MODE PASSWORD        -  3 BYTES
    ZMode: Integer; //Z MODE PASSWORD        -  3 BYTES
    PMode: Integer; //P MODE PASSWORD        -  3 BYTES
    UMode: Integer; //U MODE PASSWORD        -  3 BYTES (DP90 & DPS90 only)
    RMode: Integer; //R MODE PASSWORD        -  3 BYTES (DP90 & DPS90 only)
    DMode: Integer; //D MODE PASSWORD        -  3 BYTES (DP90 & DPS90 only)
    MMode: Integer; //M MODE PASSWORD        -  3 BYTES (DP90 & DPS90 only)
  end;

  PSMPwd = ^SMPwd;

implementation


end.

