; PLU Total File描述文件
;例子PLU_No         ,4,BCD :字段名,长度,类型
;Bytes: 0--不包含在PLU数据中的字段;大于0--PLU数据的字段
;By status: 0--固定字段;1--依赖于状态字;-1--不包含在PLU数据中的字段
;deal with or not :该字段是否需要被处理 ;0----不处理;1----需要处理的该字段
;Item              Desc(Bytes ,Type             ,Description                ,postion,deal with or not
PLU_No                 ,4    ,BCD              ,PLU_No                     ,       ,1
PLUTotal_RecSize       ,2    ,HEX              ,                           ,       ,0
PLU_EanCode            ,7    ,OTHER            ,PLU_EanCode                ,       ,1
DUMMY                  ,1    ,BCD              ,                           ,       ,0
PLU_MG                 ,2    ,BCD              ,PLU_MG                     ,       ,1
DTActPrice             ,4    ,BCD_PRICE        ,DTActPrice                 ,       ,1
DTPlanPrice            ,4    ,BCD_PRICE        ,DTPlanPrice                ,       ,1
DTPCS                  ,2    ,BCD              ,DTPCS                      ,       ,1
DTWeight               ,4    ,BCD_WEIGHT       ,DTWeight                   ,       ,1
DTProfit               ,4    ,BCD_PRICE        ,DTProfit                   ,       ,1
;TTActPrice             ,4    ,BCD_PRICE        ,TTActPrice                ,       ,0
;TTPlanPrice            ,4    ,BCD_PRICE        ,TTPlanPrice               ,       ,0
;TTPCS                  ,2    ,BCD              ,TTPCS                     ,       ,0
;TTWeight               ,4    ,BCD_WEIGHT       ,TTWeight                  ,       ,0
;TTProfit               ,4    ,BCD_PRICE        ,TTProfit                  ,       ,0
;PDTActPrice            ,4    ,BCD_PRICE        ,PDTActPrice               ,       ,0
;PDTPlanPrice           ,4    ,BCD_PRICE        ,PDTPlanPrice              ,       ,0
;PDTPCS                 ,2    ,BCD              ,PDTPCS                    ,       ,0
;PDTWeight              ,4    ,BCD_WEIGHT       ,PDTWeight                 ,       ,0
;PDTProfit              ,4    ,BCD_PRICE        ,PDTProfit                 ,       ,0
;PTTActPrice            ,4    ,BCD_PRICE        ,PTTActPrice               ,       ,0
;PTTPlanPrice           ,4    ,BCD_PRICE        ,PTTPlanPrice              ,       ,0
;PTTPCS                 ,2    ,BCD              ,PTTPCS                    ,       ,0
;PTTWeight              ,4    ,BCD_WEIGHT       ,PTTWeight                 ,       ,0
;PTTProfit              ,4    ,BCD_PRICE        ,PTTProfit                 ,       ,0
;DisDTActPrice          ,4    ,BCD_PRICE        ,DisDTActPrice             ,       ,0
;DisDTPlanPrice         ,4    ,BCD_PRICE        ,DisDTPlanPrice            ,       ,0
;DisDTPCS               ,2    ,BCD              ,DisDTPCS                  ,       ,0
;DisDTWeight            ,4    ,BCD_WEIGHT       ,DisDTWeight               ,       ,0
;DisDTProfit            ,4    ,BCD_PRICE        ,DisDTProfit               ,       ,0
;LActDate               ,3    ,BCD_DATE         ,LActDate                  ,       ,0
;DUMMY                  ,1    ,BCD              ,                          ,       ,0
;DisTTActPrice          ,4    ,BCD_PRICE        ,DisTTActPrice             ,       ,0
;DisTTPlanPrice         ,4    ,BCD_PRICE        ,DisTTPlanPrice            ,       ,0
;DisTTPCS               ,2    ,BCD              ,DisTTPCS                  ,       ,0
;DisTTWeight            ,4    ,BCD_WEIGHT       ,DisTTWeight               ,       ,0
;DisTTProfit            ,4    ,BCD_PRICE        ,DisTTProfit               ,       ,0