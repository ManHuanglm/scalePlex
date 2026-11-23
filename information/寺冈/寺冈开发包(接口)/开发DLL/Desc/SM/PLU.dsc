; PLU描述文件
;例子PLU_No         ,4,BCD :接口文件中的位置,是否操作,类型
;Bytes: 0--不包含在PLU数据中的字段;大于0--PLU数据的字段
;By status: 0--固定字段;1--依赖于状态字;-1--不包含在PLU数据中的字段
;deal with or not :该字段是否需要被处理 ;0----不处理;1----需要处理的该字段
;"*"--代表秤重标志;"#"--代表条码右侧数据
;Item                             Desc(Bytes ,Type          ,Description                             ,postion ,deal with or not
PLU_No                                  ,4   ,BCD           ,PLU_No                                  ,0       ,1               
*                                       ,0   ,              ,Weight_Flag                             ,1       ,1               
PLU_UPrice                              ,4   ,BCD_PRICE     ,PLU_UPrice                              ,3       ,1               
PLU_LBFormat1                           ,1   ,HEX           ,PLU_LBFormat1                           ,17      ,1               
PLU_LBFormat2                           ,1   ,HEX           ,PLU_LBFormat2                           ,18      ,1               
#                                       ,0   ,              ,Right_Side_data_of_item_bar             ,7       ,1               
PLU_BarFormat                           ,1   ,HEX           ,PLU_BarFormat                           ,5       ,1               
PLU_EANFlags                            ,1   ,BCD           ,PLU_EANFlags                            ,6       ,1               
PLU_EANData                             ,5   ,BCD           ,PLU_EANData                             ,8       ,1               
PLU_MG                                  ,2   ,BCD           ,PLU_MG                                  ,2       ,1               
PLU_SBDate                              ,2   ,BCD           ,PLU_SBDate                              ,23      ,1               
PLU_SBTime                              ,2   ,BCD_TIME      ,PLU_SBTime                              ,24      ,1               
PLU_UBDate                              ,2   ,BCD           ,PLU_UBDate                              ,9       ,1               
PLU_PAKDate                             ,2   ,BCD           ,PLU_PAKDate                             ,26      ,1               
PLU_PAKTime                             ,2   ,BCD_TIME      ,PLU_PAKTime                             ,27      ,1               
PLU_Cost                                ,4   ,BCD           ,PLU_Cost                                ,4       ,1               
PLU_Tare                                ,2   ,BCD           ,PLU_Tare                                ,16      ,1               
PLU_QTY                                 ,2   ,BCD           ,PLU_QTY                                 ,21      ,1               
PLU_QTYSymbol                           ,1   ,BCD           ,PLU_QTYSymbol                           ,22      ,1               
PLU_SPMessageNo                         ,1   ,BCD           ,PLU_SPMessageNo                         ,28      ,1               
PLU_IngredientNo                        ,1   ,HEX           ,PLU_IngredientNo                        ,29      ,1               
PLU_PlaceNo                             ,1   ,BCD           ,PLU_PlaceNo                             ,19      ,1               
Commodity_line1_character_size          ,1   ,HEX           ,Commodity_line1_character_size          ,11      ,1               
Commodity_line1_name                    ,100 ,ASCII_NOFONT  ,Commodity_line1_name                    ,12      ,1               
Commodity_line2_character_size          ,1   ,HEX           ,Commodity_line2_character_size          ,14      ,1               
Commodity_line2_name                    ,100 ,ASCII_NOFONT  ,Commodity_line2_name                    ,15      ,1               
Ingedients_line1_character_size         ,1   ,HEX           ,Ingedients_line1_character_size         ,46      ,1               
Ingedients_line1_name                   ,100 ,ASCII_NOFONT  ,Ingedients_line1_name                   ,47      ,1               
Ingedients_line2_character_size         ,1   ,HEX           ,Ingedients_line2_character_size         ,49      ,1               
Ingedients_line2_name                   ,100 ,ASCII_NOFONT  ,Ingedients_line2_name                   ,50      ,1               
Ingedients_line3_character_size         ,1   ,HEX           ,Ingedients_line3_character_size         ,52      ,1               
Ingedients_line3_name                   ,100 ,ASCII_NOFONT  ,Ingedients_line3_name                   ,53      ,1               
Ingedients_line4_character_size         ,1   ,HEX           ,Ingedients_line4_character_size         ,55      ,1               
Ingedients_line4_name                   ,100 ,ASCII_NOFONT  ,Ingedients_line4_name                   ,56      ,1               
Ingedients_line5_character_size         ,1   ,HEX           ,Ingedients_line5_character_size         ,58      ,1               
Ingedients_line5_name                   ,100 ,ASCII_NOFONT  ,Ingedients_line5_name                   ,59      ,1               
Special_Message_line1_character_size    ,1   ,HEX           ,Special_Message_line1_character_size    ,31      ,1               
Special_Message_line1_name              ,100 ,ASCII_NOFONT  ,Special_Message_line1_name              ,32      ,1               
Special_Message_line2_character_size    ,1   ,HEX           ,Special_Message_line2_character_size    ,34      ,1               
Special_Message_line2_name              ,100 ,ASCII_NOFONT  ,Special_Message_line2_name              ,35      ,1               
Special_Message_line3_character_size    ,1   ,HEX           ,Special_Message_line3_character_size    ,37      ,1               
Special_Message_line3_name              ,100 ,ASCII_NOFONT  ,Special_Message_line3_name              ,38      ,1               
Special_Message_line4_character_size    ,1   ,HEX           ,Special_Message_line4_character_size    ,40      ,1               
Special_Message_line4_name              ,100 ,ASCII_NOFONT  ,Special_Message_line4_name              ,41      ,1               
Special_Message_line5_character_size    ,1   ,HEX           ,Special_Message_line5_character_size    ,43      ,1               
Special_Message_line5_name              ,100 ,ASCII_NOFONT  ,Special_Message_line5_name              ,44      ,1               