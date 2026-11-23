unit ShopName;

interface
uses
  Classes,PLU;

type
  ShpName= record
    RecordNo: Integer;        //¼ÇÂ¼±àºÅ
    RecSize: Integer;
    labformat : Integer;
    Name: array[0..2] of SM_ASCII_TXT;
  end;

  PShpName = ^ShpName;

implementation


end.


