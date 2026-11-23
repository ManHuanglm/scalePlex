unit SalesTotal;

interface
uses
  Classes;

type
  SalesDaily = record
    RecordNo: Integer; //SALES TOTAL NUMBER             -  4 BYTES
    GTotal: Integer; //SALES TOTAL GRAND TOTAL        -  4 BYTES
    ReceiptNo: Integer; //SALES TOTAL RECEIPT NO         -  4 BYTES
    //SALES DAILY                    -  300 BYTES
    ResetNo: Integer; //RESET NO                       -  2 BYTES
    GQTY: Integer; //GROSS SALES QUANTITY           -  4 BYTES
    GWeight: Integer; //GROSS SALES WEIGHT             -  4 BYTES
    GAmount: Integer; //GROSS SALES AMOUNT             -  4 BYTES
    DiscCNT: Integer; //DISCOUNT COUNT                 -  4 BYTES
    DiscAMT: Integer; //DISCOUNT AMOUNT                -  4 BYTES
    PLUPCTDiscCNT: Integer; //PLU PERCENT DISCOUNT COUNT     -  4 BYTES
    PLUPCTDiscAMT: Integer; //PLU PERCENT DISCOUNT AMOUNT    -  4 BYTES
    PLUDiscCNT: Integer; //PLU AMOUNT DISCOUNT COUNT      -  4 BYTES
    PLUDiscAMT: Integer; //PLU AMOUNT DISCOUNT AMOUNT     -  4 BYTES
    PCTDiscCNT: Integer; //PERCENT DISCOUNT COUNT         -  4 BYTES
    PCTDiscAMT: Integer; //PERCENT DISCOUNT AMOUNT        -  4 BYTES
    AMTDiscCNT: Integer; //AMOUNT DISCOUNT COUNT          -  4 BYTES
    AMTDiscAMT: Integer; //AMOUNT DISCOUNT AMOUNT         -  4 BYTES
    CorrectCNT: Integer; //ITEM CORRECT COUNT             -  4 BYTES
    CorrectAMT: Integer; //ITEM CORRECT AMOUNT            -  4 BYTES
    VoidCNT: Integer; //VOID COUNT                     -  4 BYTES
    VoidAMT: Integer; //VOID AMOUNT                    -  4 BYTES
    RefundCNT: Integer; //REFUND COUNT                   -  4 BYTES
    RefundQTY: Integer; //REFUND QUANTITY                -  4 BYTES
    RefundWeight: Integer; //REFUND WEIGHT                  -  4 BYTES
    RefundAMT: Integer; //REFUND AMOUNT                  -  4 BYTES
    NetAMT: Integer; //NET SALE AMOUNT                -  4 BYTES
    NetWithoutATX: Integer; //NET SALE WITHOUT ATX           -  4 BYTES
    NetWithoutTax: Integer; //NET SALE WITHOUT TAX           -  4 BYTES
    ATXAmount: Integer; //ATX AMOUNT                     -  4 BYTES
    VATAmout: Integer; //VAT AMOUNT                     -  4 BYTES
    RoundingAMT: Integer; //SP ROUNDING FRACTION AMOUNT    -  4 BYTES
    CustomerCNT: Integer; //CUSTOMER COUNT                 -  4 BYTES
    CashCNT: Integer; //CASH SALES CUSTOMER COUNT      -  4 BYTES
    CashAMT: Integer; //CASH SALES AMOUNT              -  4 BYTES
    CreditCNT: Integer; //CREDIT SALES CUSTOMER COUNT    -  4 BYTES
    CreditAMT: Integer; //CREDIT SALES AMOUNT            -  4 BYTES
    ChequeCNT: Integer; //CHEQUE SALES CUSTOMER COUNT    -  4 BYTES
    ChequeAMT: Integer; //CHEQUE SALES AMOUNT            -  4 BYTES
    VoucherCNT: Integer; //VOUCHER SALES CUSTOMER COUNT   -  4 BYTES
    VoucherAMT: Integer; //VOUCHER SALES AMOUNT           -  4 BYTES
    PayoutCNT: Integer; //PAYOUT CUSTOMER COUNT          -  4 BYTES
    PayoutAMT: Integer; //PAYOUT SALES AMOUNT            -  4 BYTES
    RecvAccountCNT: Integer; //RECEIVED-ON-ACCOUNT COUNT      -  2 BYTES
    PaidOutCNT: Integer; //PAID OUT COUNT                 -  2 BYTES
    RecvAccountAMT: Integer; //RECEIVED-ON-ACCOUNT AMOUNT     -  4 BYTES
    RecvAccountAMTEuro: Integer; //RECEIVED-ON-ACCOUNT AMT EURO   -  4 BYTES
    PaidOutAMT: Integer; //PAID OUT AMOUNT                -  4 BYTES
    PaidOutAMTEuro: Integer; //PAID OUT AMOUNT EURO           -  4 BYTES
    CashInDrawerAMT: Integer; //CASH-IN-DRAWER AMOUNT          -  4 BYTES
    CashInDrawerAMTEuro: Integer; //CASH-IN-DRAWER AMOUNT EURO     -  4 BYTES
  end;

  PSalesDaily = ^SalesDaily;

implementation


end.

