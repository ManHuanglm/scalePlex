program Project1;

uses
  Forms,
  Unit1 in 'Unit1.pas' {Form1},
  ScaleAPI in 'ScaleAPI.pas',
  PLU in 'PLU.pas',
  SalesReceipt in 'SalesReceipt.pas',
  Stock in 'Stock.pas',
  ScaleData in 'ScaleData.pas',
  Password in 'Password.pas',
  PreSetKey in 'PreSetKey.pas',
  Traceability in 'Traceability.pas',
  ShopName in 'ShopName.pas',
  FlexiBarcode in 'FlexiBarcode.pas',
  SalesTotal in 'SalesTotal.pas';

{$R *.res}

begin
  Application.Initialize;
  Application.CreateForm(TForm1, Form1);
  Application.Run;
end.

