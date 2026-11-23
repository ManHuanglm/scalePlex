unit Traceability;

interface
uses
  Classes;

type
  Trace = record
    RecordNo: Integer; //TRACEABILITY NO.                 -  4 BYTES
    RecSize: Integer; //TRACEABILITY SIZE                -  2 BYTES
    Born: Integer; //TRACEABILITY BORN                -  2 BYTES  (4 BCD)
    Fatten: Integer; //TRACEABILITY FATTEN              -  2 BYTES  (4 BCD)
    SlaughterHouse: Integer; //TRACEABILITY SLAUGHTER HOUSE     -  2 BYTES  (4 BCD)
    SlaughterCountry: Integer; //TRACEABILITY SLAUGHTER COUNTRY   -  2 BYTES  (4 BCD)
    CuttingHall: Integer; //TRACEABILITY CUTTING HALL        -  2 BYTES  (4 BCD)
    CuttingCountry: Integer; //TRACEABILITY CUTTING COUNTRY     -  2 BYTES  (4 BCD)
    ReferenceDate: Integer; //TRACEABILITY REFERENCE DATE      -  4 BYTES  (6 BCD, DDMMYY,valid when Spec272 is 1))
    OriginCountry: Integer; //TRACEABILITY COUNTRY OF ORIGIN   -  2 BYTES  (4 BCD)
    ReferenceCode: array[0..19] of Char; //TRACEABILITY REFERENCE CODE      -  20 BYTES  (20 char)
    ReferenceType: Integer; //TRACEABILITY REFERENCE TYPE      -  1 BYTE  (1: CODE, 0: DATE)
    Dummy: Integer; //DUMMY                            -  1 BYTE
    TraceLink: Integer; //TRACEABILITY TRACE LINK          -  2 BYTES  (Not used)
    Gtin: array[0..13] of Char; //TRACEABILITY GTIN                -  14 BYTES  (14 char)
    Kind: Integer; //TRACEABILITY KIND                -  2 BYTES  (4 BCD)
    Category: Integer; //TRACEABILITY CATEGORY            -  2 BYTES  (4 BCD)
    Breed: Integer; //TRACEABILITY BREED               -  2 BYTES  (4 BCD)
    LotNum: array[0..29] of Char; //TRACEABILITY LOT NUM             -  30 BYTES  (30 char)
    ContactRef: array[0..9] of Char; //TRACEABILITY CONTACT REF         -  10 BYTES  (10 char)
    EatDate: Integer; //TRACEABILITY EAT DATE            -  4 BYTES  (6 BCD, DDMMYY)
    Weight: Integer; //TRACEABILITY WEIGHT              -  4 BYTES  (6 BCD)
    ID: Integer; //TRACEABILITY ID                  -  4 BYTES  (Not used)
    SupplierCode: array[0..29] of Char; //TRACEABILITY SUPPLIER CODE       -  30 BYTES  (30 char)
    SupplierName: array[0..29] of Char; //TRACEABILITY SUPPLIER NAME       -  30 BYTES  (30 char)
    SupplierAddr1: array[0..29] of Char; //TRACEABILITY SUPPLIER ADDR1      -  30 BYTES  (30 char)
    SupplierAddr2: array[0..29] of Char; //TRACEABILITY SUPPLIER ADDR2      -  30 BYTES  (30 char)
  end;

  PTrace = ^Trace;

implementation


end.

