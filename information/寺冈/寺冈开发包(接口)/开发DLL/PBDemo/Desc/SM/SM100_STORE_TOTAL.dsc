; PLU Total File描述文件
;例子PLU_No         ,4,BCD :字段名,长度,类型
;Bytes: 0--不包含在PLU数据中的字段;大于0--PLU数据的字段
;By status: 0--固定字段;1--依赖于状态字;-1--不包含在PLU数据中的字段
;deal with or not :该字段是否需要被处理 ;0----不处理;1----需要处理的该字段
;Item                      Desc(Bytes ,Type ,Description                ,postion,deal with or not
STORE_TOTAL_NUMBER              ,4   ,BCD        ,STORE_TOTAL_NUMBER         ,       ,1
STORE_TOTAL_RECORD_SIZE         ,2   ,HEX        ,STORE_TOTAL_RECORD_SIZE    ,       ,0
NO_OF_CUSTOMER                  ,4   ,HEX        ,NO_OF_CUSTOMER             ,       ,1
NO_OF_ITEM                      ,4   ,HEX        ,NO_OF_ITEM                 ,       ,1
ACTUAL_SALE                     ,4   ,HEX_PRICE  ,ACTUAL_SALE                ,       ,1
PLANNED_SALE                    ,4   ,HEX_PRICE  ,PLANNED_SALE               ,       ,1
NOMINAL_SALE                    ,4   ,HEX_PRICE  ,NOMINAL_SALE               ,       ,1
DISCOUNT_PRICE                  ,4   ,HEX_PRICE  ,DISCOUNT_PRICE             ,       ,1
WEIGHT                          ,4   ,HEX_WEIGHT ,WEIGHT                     ,       ,1
REFUND_ITEM_COUNT               ,2   ,HEX        ,REFUND_ITEM_COUNT          ,       ,1
REFUND_PRICE                    ,4   ,HEX_PRICE  ,REFUND_PRICE               ,       ,1
;ALL_PICKOUT_REFUND_CNT          ,2   ,HEX         ,ALL_PICKOUT_REFUND_CNT     ,       ,0
;ALL_PICKOUT_REFUND_PRICE        ,4   ,HEX_PRICE   ,ALL_PICKOUT_REFUND_PRICE   ,       ,0
;ALL_RCT_CORRECT_ITEM_CNT        ,2   ,HEX         ,ALL_RCT_CORRECT_ITEM_CNT   ,       ,0
;ALL_RCT_CORRECT_RCT_CNT         ,2   ,HEX         ,ALL_RCT_CORRECT_RCT_CNT    ,       ,0
;ALL_RCT_CORRECT_PRICE           ,4   ,HEX_PRICE   ,ALL_RCT_CORRECT_PRICE      ,       ,0
;VAT_NO._OF_TAXABLE_ITEM         ,4   ,HEX         ,VAT_NO._OF_TAXABLE_ITEM    ,       ,0
;VAT_AMOUNT_OF_TAX               ,4   ,HEX         ,VAT_AMOUNT_OF_TAX          ,       ,0
;VAT_NO._OF_EXEMPTED_ITEM        ,4   ,HEX         ,VAT_NO._OF_EXEMPTED_ITEM   ,       ,0
;VAT_AMT_OF_EXEMPTED_TAX         ,4   ,HEX         ,VAT_AMT_OF_EXEMPTED_TAX    ,       ,0
;ATX_NO._OF_TAXABLE_ITEM         ,4   ,HEX         ,ATX_NO._OF_TAXABLE_ITEM    ,       ,0
;ATX_AMOUNT_OF_TAX               ,4   ,HEX         ,ATX_AMOUNT_OF_TAX          ,       ,0
;ATX_NO._OF_EXEMPTED_ITEM        ,4   ,HEX         ,ATX_NO._OF_EXEMPTED_ITEM   ,       ,0
;ATX_AMT_OF_EXEMPTED_TAX         ,4   ,HEX         ,ATX_AMT_OF_EXEMPTED_TAX    ,       ,0
