package com.scaleplex.core.model

/**
 * 商品类别（对齐顶尖 LiNK64「类别资料」）：编号 + 名称。
 * [PluItem.mainGroup] 存类别编号，列表显示时映射到 [name]。
 */
data class Category(
    val no: Int,
    val name: String = "",
)
