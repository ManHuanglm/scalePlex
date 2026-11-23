unit SalesReceipt;

interface
uses
  Classes;

type
  ReceiptDetail = record
    RecordNo: Integer; //*RECEIPT BUFFER RECORD#          -4 BYTES
    RecordSize: Integer; //RECEIPT BUFFER SIZE              -2 BYTES
    ItemStatus: Integer; //ITEM STATUS                      -4 BYTES
    PluNo: Integer; //PLU NUMBER                       -4 BYTES(BCD)
    MG: Integer; //MAIN GROUP NUMBER                -4 BYTES(BCD)
    QTY: Integer; //QUANTITY                         -4 BYTES
    Weight: Integer; //WEIGHT                           -4 BYTES
    Uprice: Integer; //UNIT PRICE                       -4 BYTES
    UpriceAfDisc: Integer; //UNIT PRICE AFTER DISCOUNT        -4 BYTES
    UPDiscAmt: Integer; //U.P. DISCOUNT AMOUNT             -4 BYTES
    NPrice: Integer; //NOMINAL PRICE                    -4 BYTES
    ActPrice: Integer; //ACTUAL PRICE                     -4 BYTES
    PriceAfDiscTax: Integer; //PRICE AFTER DISCOUNT AND TAX     -4 BYTES
    PriceAfSTTLDisc: Integer; //PRICE AFTER SUB TOTAL DISC       -4 BYTES
    PriceAfUPOV: Integer; //PRICE AFTER UP OVERRIDE          -4 BYTES
    TTLPriceDiscAmt: Integer; //TOTAL PRICE DISCOUNT AMOUNT      -4 BYTES
    ItPriceAFTTLRD: Integer; //ITEM PRICE AFTER TOTAL ROUNDING  -4 BYTES
    TaxRtNo: Integer; //TAX RATE NUMBER                  -1 BYTE (binary)
    Dummy1: Integer; //**DUMMY                          -1 BYTE
    TaxRt: Integer; //TAX RATE                         -2 BYTES
    TaxVal: Integer; //TAX VALUE                        -4 BYTES
    TaxValAfSTTLDisc: Integer; //TAX VALUE AFTER SUB TOTAL DISC   -4 BYTES
    SPPriceNo: Integer; //SPECIAL PRICE NUMBER             -1 BYTE (binary)
    Dummy2: Integer; //**DUMMY                          -1 BYTE
    ScaleNo: Integer; //SCALE NUMBER                     -3 BYTES
    Dummy3: Integer; //**DUMMY                          -1 BYTE
    ValOfPoint1: Integer; //VALUE OF POINT 1                 -4 BYTES
    PriceOfPoint1: Integer; //PRICE OF POINT 1                 -4 BYTES
    ValOfPoint2: Integer; //VALUE OF POINT 2                 -4 BYTES
    PriceOfPoint2: Integer; //PRICE OF POINT 2                 -4 BYTES
    TareVal: Integer; //TARE VALUE                       -4 BYTES
    StPointDiscStat: Integer; //STEP POINT DISC STATUS           -1 BYTE
    MarkdStat: Integer; //MARKDOWN STATUS                  -1 BYTE
    SaleDate: Integer; //JIN RECBUF WEIGHT (FOR TW)       -4 BYTES
    SaleTime: Integer; //JIN RECBUF STATUS (FOR TW)       -2 BYTES
    PublicFreeWT: Integer; //PUBLICITY FREE WEIGHT            -4 BYTES
    PublicBonus: Integer; //PUBLICITY BONUS POINT            -4 BYTES
    PublicFreeQTY: Integer; //PUBLICITY FREE QUANTITY          -4 BYTES
      //TRACEABILITY ----start----
    BornCountry: Integer; //TRACEABILITY BORN COUNTRY        -2 BYTES
    FatCountry: Integer; //TRACEABILITY FATTEN COUNTRY      -2 BYTES
    SlaugHouse: Integer; //TRACEABILITY SLAUGHTER HOUSE     -2 BYTES
    SlaugCountry: Integer; //TRACEABILITY SLAUGHTER COUNTRY   -2 BYTES
    CutHall: Integer; //TRACEABILITY CUTTING HALL        -2 BYTES
    CutCountry: Integer; //TRACEABILITY CUTTING COUNTRY     -2 BYTES
    ReferDate: Integer; //TRACEABILITY REFERENCE DATE      -4 BYTES (date format)
    OriginCountry: Integer; //TRACEABILITY ORIGIN COUNTRY      -2 BYTES
    ReferCode: array[0..19] of Char; //TRACEABILITY REFERENCE CODE      -20 BYTES (ASCII)
    ReferType: Integer; //TRACEABILITY REFERENCE TYPE      -1 BYTE (0 ¨C use ref. Date;  1¡ªuse ref. Code)
    ReferSet: Integer; //TRACEABILITY REFERENCE SET       -1 BYTE (RESERVE BYTE)
      //TRACEABILITY ----end----
//    BfPriceKE: Integer; //BEFORE PRICE                     -4 BYTES (Korea only)
//    ItemCodeKE: Integer; //ITEM CODE                        -7 BYTES (Korea only)
//    IPAddrKE: Integer; //IP ADDRESS                       -1 BYTE  (Korea only)
//    ItemNameKE: string; //ITEM NAME                        -50 BYTES (Korea only)
    PayTTLNo: Integer; //PAYMENT TTL NUM                  -1 BYTE
    Dummy4: Integer; //DUMMY                            -1 BYTE
    PayNo1: Integer; //PAYMENT NUM 1                    -2 BYTES
    PayQTY1: Integer; //PAYMENT QTY 1                    -4 BYTES
    PayAmt1: Integer; //PAYMENT AMOUNT 1                 -4 BYTES
    PayAMtEuro1: Integer; //PAYMENT AMOUNT EURO 1            -4 BYTES
    Reserve1: Integer; //RESERVE                          -2 BYTES
    PayNo2: Integer; //PAYMENT NUM 2                    -2 BYTES
    PayQTY2: Integer; //PAYMENT QTY 2                    -4 BYTES
    PayAmt2: Integer; //PAYMENT AMOUNT 2                 -4 BYTES
    PayAMtEuro2: Integer; //PAYMENT AMOUNT EURO 2            -4 BYTES
    Reserve2: Integer; //RESERVE                          -2 BYTES
    PayNo3: Integer; //PAYMENT NUM 3                    -2 BYTES
    PayQTY3: Integer; //PAYMENT QTY 3                    -4 BYTES
    PayAmt3: Integer; //PAYMENT AMOUNT 3                 -4 BYTES
    PayAMtEuro3: Integer; //PAYMENT AMOUNT EURO 3            -4 BYTES
    Reserve3: Integer; //RESERVE                          -2 BYTES
      //TRACEABILITY ----start----
    Gtin: array[0..13] of Char; //TRACEABILITY GTIN                -14 BYTES
    Kind: Integer; //TRACEABILITY KIND                -2 BYTES
    Category: Integer; //TRACEABILITY CATEGORY            -2 BYTES
    Breed: Integer; //TRACEABILITY BREED               -2 BYTES
    LotNo: array[0..29] of Char; //TRACEABILITY LOT NUM             -30 BYTES
    ContactRef: array[0..9] of Char; //TRACEABILITY CONTACT REF.        -10 BYTES
    EatDate: Integer; //TRACEABILITY EAT DATE            -4 BYTES
    TraceWT: Integer; //TRACEABILITY WEIGHT              -4 BYTES
    TraceID: Integer; //TRACEABILITY ID                  -4 BYTES
      //TRACEABILITY ----end----
    PriceBfDisc: Integer; //PRICE BEFORE DISCOUNT            -4 BYTES (NOT KOREA)
    ItemCode: array[0..13] of Char; //ITEM CODE                        -7 BYTES (NOT KOREA)
    IPAddrTail: Integer; //IP ADDRESS TAIL                  -1 BYTE (NOT KOREA)
    ItemName: array[0..49] of Char; //ITEM NAME                        -50 BYTES (NOT KOREA)
    SellDate: Integer; //PLU SELL BY DATE                 -4 BYTES
    PluEan: array[0..13] of Char; //PLU EAN                          -7 BYTES
    ScalePtNo: Integer; //SCALE PORT NO                    -1 BYTE (HEX)
    Dummy5: Integer; //Dummy5                          -2 BYTE (HEX)
    ClerkNo: Integer; //CLERK CODE                       -4 BYTES (BCD)
  end;

  PReceiptDetail = ^ReceiptDetail;

  ReceiptTotal = record
    RecordNo: Integer; //*RECEIPT NUMBER                  -4 BYTES
    RecordSize: Integer; //TOTAL RECEIPT RECORD SIZE        -2 BYTES
    NTrans: Integer; //TOTAL NOMINAL TRANSACTION        -1 BYTE (includes correction data)
    BufStat: Integer; //RECEIPT BUFFER STATUS 1          -1 BYTE
    RCTNo: Integer; //TOTAL RECEIPT NUMBER             -2 BYTES
    ActTrans: Integer; //TOTAL ACTUAL TRANSACTION         -1 BYTE (excludes correction data)
    RefundTrans: Integer; //TOTAL REFUND TRANSACTION         -1 BYTE
    NewPLUTrans: Integer; //NEW PLU TRANSACTION              -1 BYTE
    ClerkName: array[0..16] of Char; //TOTAL CLERK NAME                 -17 BYTES
    AdSubStat: Integer; //ADD/SUBTRACT STATUS              -1 BYTE
    Dummy1: Integer; //**DUMMY                          -1 BYTE
    Weight: Integer; //TOTAL WEIGHT                     -4 BYTES
    QTY: Integer; //TOTAL QUANTITY                   -4 BYTES
    SubGAmt: Integer; //SUBTOTAL GROSS AMOUNT            -4 BYTES (includes discount exemption)
    NPrice: Integer; //TOTAL NOMINAL PRICE              -4 BYTES
    SubNetAmt: Integer; //SUBTOTAL NET AMOUNT              -4 BYTES (excludes discount exemption)
    TSubNetAmt: Integer; //TEMP SUBTOTAL NET AMOUNT         -4 BYTES
    PriceWTax: Integer; //TOTAL PRICE WITH TAX             -4 BYTES
    TPriceWTax: Integer; //TEMP TOTAL PRICE WITH TAX        -4 BYTES
    AmtOfATax: Integer; //TOTAL AMOUNT OF ADD-ON TAX       -4 BYTES
    AmtOfVTax: Integer; //TOTAL AMOUNT OF VAT TAX          -4 BYTES
    TAmtOfATax: Integer; //TEMP TOTAL AMOUNT OF ADD-ON TAX  -4 BYTES
    TAmtOfVTax: Integer; //TEMP TOTAL AMOUNT OF VAT TAX     -4 BYTES
    SubDiscAmt: Integer; //SUBTOTAL DISCOUNT AMOUNT         -4 BYTES
    SubDiscP: Integer; //SUBTOTAL DISCOUNT PERCENTAGE     -4 BYTES
    SubItDiscAmt: Integer; //SUBTOTAL ITEM DISC AMOUNT        -4 BYTES
    SubItDiscRem: Integer; //SUBTOTAL ITEM DISC REMAINER      -4 BYTES
    AmtOfRebate: Integer; //TOTAL AMOUNT OF REBATE           -4 BYTES
    CashRecv: Integer; //TOTAL CASH RECEIVED              -4 BYTES
    NoOfCashPay: Integer; //TOTAL # OF CASH PAYMENT          -2 BYTES
    ValOfCheques: Integer; //TOTAL VALUE OF CHEQUES           -4 BYTES
    NoOfCheques: Integer; //TOTAL # OF CHEQUES               -2 BYTES
    ValOfVoucher: Integer; //TOTAL VALUE OF VOUCHER           -4 BYTES
    NoOfVoucher: Integer; //TOTAL # OF VOUCHER               -2 BYTES
    FCustomerNo: Integer; //FLOAT CUSTOMER NUMBER            -2 BYTES (HEX)
    FCustomerTbl: Integer; //FLOAT CUSTOMER TABLE             -4 BYTES
    ValOfCredit: Integer; //TOTAL VAULE OF CREDIT            -4 BYTES
    NoOfCredit: Integer; //TOTAL # OF CREDIT                -2 BYTES
    Dummy2: Integer; //TOTAL REAL TIME DATE             -1 BYTE
    SaleDate: Integer; //TOTAL REAL TIME DATE             -4 BYTES
    Dummy3: Integer; //Dummy2                           -2 BYTES
    SaleTime: Integer; //TOTAL REAL TIME TIME             -2 BYTES
    ScalePtNo: Integer; //SCALE PORT NO                  -2 BYTES
    ClerkNo: Integer; //TOTAL REAL TIME CLERK            -2 BYTES
    RTRCTNo: Integer; //TOTAL REAL TIME RCT #            -2 BYTES
    ScaleNo: Integer; //TOTAL REAL TIME SCALE #          -4 BYTES
    TenderAmt: Integer; //TOTAL TENDER AMOUNT              -4 BYTES
    ChangeAmt: Integer; //TOTAL CHANGE AMOUNT              -4 BYTES
    DiscAmt: Integer; //TOTAL DISC AMOUNT                -4 BYTES
    NetAmt: Integer; //TOTAL NET AMOUNT                 -4 BYTES
    CustomerNo: Integer; //TOTAL NO OF CUSTOMER             -4 BYTES
    Barcode: array[0..15] of Char; //TOTAL BARCODE                    -8 BYTES
    EFTChange: Integer; //TOTAL EFT. CHANGE                -4 BYTES
    EFTChangeNo: Integer; //TOTAL EFT. CHANGE NO             -2 BYTES
    Bonus: Integer; //TOTAL BONUS POINT                -4 BYTES
    AmtWGST: Integer; //TOTAL AMT WITH GST               -4 BYTES
    AmtWOGST: Integer; //TOTAL AMT W/O GST                -4 BYTES
    RoundingAmt: Integer; //TOTAL ROUNDING AMT               -4 BYTES
    PayNo: Integer; //TOTAL PAYMENT NUMBER             -1 BYTE
    Dummy4: Integer; //DUMMY4                            -1 BYTE
    PayNo1: Integer; //PAYMENT NUMBER 1                 -2 BYTES
    PayQTY1: Integer; //PAYMENT QUANTITY 1               -4 BYTES
    PayAmtL1: Integer; //PAYMENT AMOUNT LOCAL 1           -4 BYTES
    PayAmtE1: Integer; //PAYMENT AMOUNT EURO 1            -4 BYTES
    Reserve1: Integer; //RESERVE1                         -2 BYTES
    PayNo2: Integer; //PAYMENT NUMBER 2                 -2 BYTES
    PayQTY2: Integer; //PAYMENT QUANTITY 2               -4 BYTES
    PayAmtL2: Integer; //PAYMENT AMOUNT LOCAL 2           -4 BYTES
    PayAmtE2: Integer; //PAYMENT AMOUNT EURO 2            -4 BYTES
    Reserve2: Integer; //RESERVE2                         -2 BYTES
    PayNo3: Integer; //PAYMENT NUMBER 3                 -2 BYTES
    PayQTY3: Integer; //PAYMENT QUANTITY 3               -4 BYTES
    PayAmtL3: Integer; //PAYMENT AMOUNT LOCAL 3           -4 BYTES
    PayAmtE3: Integer; //PAYMENT AMOUNT EURO 3            -4 BYTES
    Reserve3: Integer; //RESERVE3                         -2 BYTES
    PayNo4: Integer; //PAYMENT NUMBER 4                 -2 BYTES
    PayQTY4: Integer; //PAYMENT QUANTITY 4               -4 BYTES
    PayAmtL4: Integer; //PAYMENT AMOUNT LOCAL 4           -4 BYTES
    PayAmtE4: Integer; //PAYMENT AMOUNT EURO 4            -4 BYTES
    Reserve4: Integer; //RESERVE4                         -2 BYTES
    PayNo5: Integer; //PAYMENT NUMBER 5                 -2 BYTES
    PayQTY5: Integer; //PAYMENT QUANTITY 5               -4 BYTES
    PayAmtL5: Integer; //PAYMENT AMOUNT LOCAL 5           -4 BYTES
    PayAmtE5: Integer; //PAYMENT AMOUNT EURO 5            -4 BYTES
    Reserve5: Integer; //RESERVE5                         -2 BYTES
    PayNo6: Integer; //PAYMENT NUMBER 6                 -2 BYTES
    PayQTY6: Integer; //PAYMENT QUANTITY 6               -4 BYTES
    PayAmtL6: Integer; //PAYMENT AMOUNT LOCAL 6           -4 BYTES
    PayAmtE6: Integer; //PAYMENT AMOUNT EURO 6            -4 BYTES
    Reserve6: Integer; //RESERVE6                         -2 BYTES
    PayNo7: Integer; //PAYMENT NUMBER 7                 -2 BYTES
    PayQTY7: Integer; //PAYMENT QUANTITY 7               -4 BYTES
    PayAmtL7: Integer; //PAYMENT AMOUNT LOCAL 7           -4 BYTES
    PayAmtE7: Integer; //PAYMENT AMOUNT EURO 7            -4 BYTES
    Reserve7: Integer; //RESERVE7                         -2 BYTES
    PayNo8: Integer; //PAYMENT NUMBER 8                 -2 BYTES
    PayQTY8: Integer; //PAYMENT QUANTITY 8               -4 BYTES
    PayAmtL8: Integer; //PAYMENT AMOUNT LOCAL 8           -4 BYTES
    PayAmtE8: Integer; //PAYMENT AMOUNT EURO 8            -4 BYTES
    Reserve8: Integer; //RESERVE8                         -2 BYTES
    PayNo9: Integer; //PAYMENT NUMBER 9                 -2 BYTES
    PayQTY9: Integer; //PAYMENT QUANTITY 9               -4 BYTES
    PayAmtL9: Integer; //PAYMENT AMOUNT LOCAL 9           -4 BYTES
    PayAmtE9: Integer; //PAYMENT AMOUNT EURO 9            -4 BYTES
    Reserve9: Integer; //RESERVE9                         -2 BYTES
    PayNo10: Integer; //PAYMENT NUMBER 10                -2 BYTES
    PayQTY10: Integer; //PAYMENT QUANTITY 10              -4 BYTES
    PayAmtL10: Integer; //PAYMENT AMOUNT LOCAL 10          -4 BYTES
    PayAmtE10: Integer; //PAYMENT AMOUNT EURO 10           -4 BYTES
    Reserve10: Integer; //RESERVE10                        -2 BYTES
    PayNo11: Integer; //PAYMENT NUMBER 11                -2 BYTES
    PayQTY11: Integer; //PAYMENT QUANTITY 11              -4 BYTES
    PayAmtL11: Integer; //PAYMENT AMOUNT LOCAL 11          -4 BYTES
    PayAmtE11: Integer; //PAYMENT AMOUNT EURO 11           -4 BYTES
    Reserve11: Integer; //RESERVE11                        -2 BYTES
    PayNo12: Integer; //PAYMENT NUMBER 12                -2 BYTES
    PayQTY12: Integer; //PAYMENT QUANTITY 12              -4 BYTES
    PayAmtL12: Integer; //PAYMENT AMOUNT LOCAL 12          -4 BYTES
    PayAmtE12: Integer; //PAYMENT AMOUNT EURO 12           -4 BYTES
    Reserve12: Integer; //RESERVE12                        -2 BYTES
    PayNo13: Integer; //PAYMENT NUMBER 13                -2 BYTES
    PayQTY13: Integer; //PAYMENT QUANTITY 13              -4 BYTES
    PayAmtL13: Integer; //PAYMENT AMOUNT LOCAL 13          -4 BYTES
    PayAmtE13: Integer; //PAYMENT AMOUNT EURO 13           -4 BYTES
    Reserve13: Integer; //RESERVE13                        -2 BYTES
    PayNo14: Integer; //PAYMENT NUMBER 14                -2 BYTES
    PayQTY14: Integer; //PAYMENT QUANTITY 14              -4 BYTES
    PayAmtL14: Integer; //PAYMENT AMOUNT LOCAL 14          -4 BYTES
    PayAmtE14: Integer; //PAYMENT AMOUNT EURO 14           -4 BYTES
    Reserve14: Integer; //RESERVE14                        -2 BYTES
    PayNo15: Integer; //PAYMENT NUMBER 15                -2 BYTES
    PayQTY15: Integer; //PAYMENT QUANTITY 15              -4 BYTES
    PayAmtL15: Integer; //PAYMENT AMOUNT LOCAL 15          -4 BYTES
    PayAmtE15: Integer; //PAYMENT AMOUNT EURO 15           -4 BYTES
    Reserve15: Integer; //RESERVE15                        -2 BYTES
    PayNo16: Integer; //PAYMENT NUMBER 16                -2 BYTES
    PayQTY16: Integer; //PAYMENT QUANTITY 16              -4 BYTES
    PayAmtL16: Integer; //PAYMENT AMOUNT LOCAL 16          -4 BYTES
    PayAmtE16: Integer; //PAYMENT AMOUNT EURO 16           -4 BYTES
    Reserve16: Integer; //RESERVE16                        -2 BYTES
    RCTTaxAmt1: Integer; //TOTAL RECEIPT TAX AMT1           -4 BYTES
    RCTTaxAmt2: Integer; //TOTAL RECEIPT TAX AMT2           -4 BYTES
    RCTTaxAmt3: Integer; //TOTAL RECEIPT TAX AMT3           -4 BYTES
    RCTTaxAmt4: Integer; //TOTAL RECEIPT TAX AMT4           -4 BYTES
    RCTTaxAmt5: Integer; //TOTAL RECEIPT TAX AMT5           -4 BYTES
    RCTTaxAmt6: Integer; //TOTAL RECEIPT TAX AMT6           -4 BYTES
    RCTTaxAmt7: Integer; //TOTAL RECEIPT TAX AMT7           -4 BYTES
    RCTTaxAmt8: Integer; //TOTAL RECEIPT TAX AMT8           -4 BYTES
    RCTTaxAmt9: Integer; //TOTAL RECEIPT TAX AMT9           -4 BYTES
    RCTTaxAmt10: Integer; //TOTAL RECEIPT TAX AMT10          -4 BYTES
    AmtETax1: Integer; //TOTAL AMT EXCLUDE TAX1           -4 BYTES
    AmtETax2: Integer; //TOTAL AMT EXCLUDE TAX2           -4 BYTES
    AmtETax3: Integer; //TOTAL AMT EXCLUDE TAX3           -4 BYTES
    AmtETax4: Integer; //TOTAL AMT EXCLUDE TAX4           -4 BYTES
    AmtETax5: Integer; //TOTAL AMT EXCLUDE TAX5           -4 BYTES
    AmtETax6: Integer; //TOTAL AMT EXCLUDE TAX6           -4 BYTES
    AmtETax7: Integer; //TOTAL AMT EXCLUDE TAX7           -4 BYTES
    AmtETax8: Integer; //TOTAL AMT EXCLUDE TAX8           -4 BYTES
    AmtETax9: Integer; //TOTAL AMT EXCLUDE TAX9           -4 BYTES
    AmtETax10: Integer; //TOTAL AMT EXCLUDE TAX10          -4 BYTES
    CreditRecNo: Integer; //CREDIT RECORD #                  -4 BYTES
  end;

  PReceiptTotal = ^ReceiptTotal;

  DelRecords = array of Integer;

  Plu_T_Transaction = record

    RecordNo: Integer; //T_PLU_TRANS_NO        -  4 BYTES (BCD)
    PLUNo: Integer; //T_PLTRANS_PLUNO       -  4 BYTES (BCD)
    UPrice: Integer; //T_PLU_TRANS_UP        -  4 BYTES (BCD)
    Weight: Integer; //T_PLU_TRANS_WT        -  4 BYTES (BCD)
    QTY: Integer; //T_PLU_TRANS_QTY       -  2 BYTES (BCD)
    TPrice: Integer; //T_PLU_TRANS_TP        -  4 BYTES (BCD)
    Date: Integer; //T_PLU_TRANS_DATE      -  3 BYTES (BCD)
    Time: Integer; //T_PLU_TRANS_TIME      -  2 BYTES (BCD)
    TraceCode: array[0..8] of Char; //Traceability Code     -  5 Bytes (9 BCD)
    Reserve: Integer; //T_PLU_TRANS_RESERVE   -  5 BYTES
    ScaleNo: Integer; //SCALE NO              -  1 BYTE (Hex)
    BPrice: Integer; //BEFORE PRICE          -  4 BYTES(BCD)
    APrice: Integer; //AFTER PRICE           -  4 BYTES(BCD)
    ItemCode: array[0..13] of Char; //ITEM CODE             -  7 BYTES
    ItemName: array[0..49] of Char; //ITEM NAME             -  50 BYTES(Char Size + Char No. + 48 Char)
  end;

  PPlu_T_Transaction= ^Plu_T_Transaction;

implementation


end.

