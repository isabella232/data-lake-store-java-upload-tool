package com.starbucks.analytics.db.SqlServer

import com.starbucks.analytics.db.{ SchemaInfo, SqlGenerator }

/**
 * Implementation of the sql generator trait for Oracle
 */
object SqlServerSqlGenerator extends SqlGenerator {
  // Generates the sql statement to fetch the metadata
  override def getPartitions(
    owner:         String,
    tables:        List[String],
    partitions:    Option[List[String]],
    subPartitions: Option[List[String]]
  ): String = {
    val builder: StringBuilder = new StringBuilder
    builder ++=
      s"""SELECT T.OWNER, T.TABLE_NAME, P.PARTITION_NAME, SP.SUBPARTITION_NAME FROM
         | ALL_TABLES T
         | LEFT OUTER JOIN ALL_TAB_PARTITIONS P ON
         | T.TABLE_NAME = P.TABLE_NAME AND T.OWNER = P.TABLE_OWNER
         | LEFT OUTER JOIN ALL_TAB_SUBPARTITIONS SP ON
         | P.TABLE_NAME = SP.TABLE_NAME and P.PARTITION_NAME = SP.PARTITION_NAME AND P.TABLE_OWNER = SP.TABLE_OWNER
         | WHERE T.OWNER = '${owner.toUpperCase}' AND
         | T.TABLE_NAME IN
         | (${tables map (table => s"'${table.toUpperCase}'") mkString ", "})
       """.stripMargin
    if (partitions.isDefined && partitions.get.nonEmpty) {
      builder ++= " AND( "
      builder ++= partitions.get.foldLeft(new StringBuilder) {
        (sb, s) => sb append s"OR P.PARTITION_NAME LIKE '%${s.toUpperCase}%' "
      }.delete(0, 3).toString
      builder ++= ")"
    }
    if (subPartitions.isDefined && subPartitions.get.nonEmpty) {
      builder ++= " AND( "
      builder ++= subPartitions.get.foldLeft(new StringBuilder) {
        (sb, s) => sb append s"OR SP.SUBPARTITION_NAME LIKE '%${s.toUpperCase}%' "
      }.delete(0, 3).toString
      builder ++= ")"
    }

    builder ++=
      s"""
         | UNION ALL
         | SELECT V.OWNER, V.VIEW_NAME AS TABLE_NAME, NULL AS PARTITION_NAME, NULL AS SUBPARTITION_NAME FROM
         | ALL_VIEWS V WHERE
         | V.OWNER = '${owner.toUpperCase}' AND
         | V.VIEW_NAME IN
         | (${tables map (table => s"'${table.toUpperCase}'") mkString ", "})
       """.stripMargin

    builder.toString()
  }

  // Generates a sql statement to fetch the column names
  override def getColumnNames(
    owner:     String,
    tableName: String
  ): String = {
    val builder: StringBuilder = new StringBuilder
    builder ++=
      s"""SELECT C.NAME FROM SYS.SCHEMAS S
         | INNER JOIN SYS.TABLES T ON S.SCHEMA_ID = T.SCHEMA_ID
         | INNER JOIN SYS.COLUMNS C ON T.OBJECT_ID = C.OBJECT_ID
         | WHERE UPPER(S.NAME) = '${owner.toUpperCase}' AND
         | UPPER(T.NAME) ='${tableName.toUpperCase}'
         | ORDER BY COLUMN_ID
       """.stripMargin
    builder.toString()
  }

  // Generates a sql statement to fetch data given
  override def getData(
    schemaInfo: SchemaInfo,
    columns:    List[String],
    predicate:  Option[String]
  ): String = {
    schemaInfo.partitionName match {
      case Some(p) =>
        s"""
               |SELECT ${columns mkString ","}
               | FROM ${schemaInfo.owner}.${schemaInfo.tableName} WITH(NOLOCK)
               | PARTITION($p)
         """.stripMargin
      case None =>
        predicate match {
          case Some(pr) =>
            s"""
                   |SELECT ${columns mkString ","}
                   | FROM ${schemaInfo.owner}.${schemaInfo.tableName} WITH(NOLOCK)
                   | WHERE $pr
             """.stripMargin
          case None =>
            s"""
                   |SELECT ${columns mkString ","}
                   | FROM ${schemaInfo.owner}.${schemaInfo.tableName} WITH(NOLOCK)
             """.stripMargin
        }
    }
  }
}
