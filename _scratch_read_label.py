# -*- coding: utf-8 -*-
import openpyxl, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
p = r'C:\Users\Administrator\.dsh\office-kit\outputs\session-c047c5e6eb1d486de0b93c4a\9627c69c-8478-4a22-8163-f8deb0c5ca0b\标签格式1.xlsx'
wb = openpyxl.load_workbook(p, data_only=True)
for ws in wb.worksheets:
    print('===== SHEET:', ws.title, '=====')
    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if any(c is not None and str(c).strip() != '' for c in row):
            print(i, [c for c in row])
        if i > 80:
            break
