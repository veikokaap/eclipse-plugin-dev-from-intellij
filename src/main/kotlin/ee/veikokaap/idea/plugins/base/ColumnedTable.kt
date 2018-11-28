package ee.veikokaap.idea.plugins.base

import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.forEachGuaranteed
import ee.veikokaap.idea.plugins.base.util.fillBoth
import ee.veikokaap.idea.plugins.base.util.gridBagConstraints
import ee.veikokaap.idea.plugins.base.util.withInsets
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.*
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel
import kotlin.properties.Delegates

class ColumnedTable<T>(val columns: List<TableColumn<T, out Any?>>) : JPanel() {
  
  private var refreshing: Boolean = false
  
  var data: List<T> by Delegates.observable(emptyList()) { property, oldValue, newValue ->
    refreshTable()
  }
  
  private val tableModel = object : DefaultTableModel(getTableData(), getColumnNames()) {
    override fun isCellEditable(row: Int, column: Int): Boolean {
      return columns[column] is EditableTableColumn
    }
    
    override fun getColumnClass(columnIndex: Int): Class<*> {
      return columns[columnIndex].typeClazz
    }
  }
  private val table = JBTable(tableModel)
  
  init {
    layout = GridBagLayout()
    add(table, gridBagConstraints().fillBoth(weightx = 1.0, weighty = 1.0).withInsets(Insets(3, 3, 3, 3)))
    table.setShowColumns(true)
    table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    table.border = LineBorder(JBColor.border())
    tableModel.addTableModelListener { tableModelEvent ->
      tableModel.dataVector.zip(data)
          .flatMap { (potentiallyChangedRow, rowData) ->
            columns.mapIndexedNotNull { index, column ->
              val oldCellData = column.rowToCellDataMapper(rowData)
              val newCellData = (potentiallyChangedRow as Vector<out Any?>)[index]
              when {
                oldCellData != newCellData -> Triple(column as EditableTableColumn<T, Any?>, rowData, newCellData)
                else -> null
              }
            }
          }
          .forEachGuaranteed { (column, rowData, changedCellValue) ->
            column.valueChangeListener(rowData, changedCellValue)
          }
    }
    refreshTable()
  }
  
  private fun refreshTable() {
    if (!refreshing) {
      refreshing = true
      tableModel.setDataVector(getTableData(), getColumnNames())
      shrinkLastColumn()
      refreshing = false
    }
  }
  
  private fun shrinkLastColumn() {
    if (table.columnCount > 0) {
      val lastColumn = table.columnModel.getColumn(table.columnCount - 1)
      lastColumn.preferredWidth = 0
      lastColumn.minWidth = 0
    }
  }
  
  private fun getTableData(): Array<Array<Any?>> {
    val tableData = data.map { rowData ->
      columns.map { column -> column.rowToCellDataMapper(rowData) }.toTypedArray()
    }.toTypedArray()
    return tableData
  }
  
  fun getColumnNames(): Array<String> {
    return columns.map { it.columnName }.toTypedArray()
  }
}

open class TableColumn<T, V>(val columnName: String, val typeClazz: Class<out V>, val rowToCellDataMapper: (rowData: T) -> V)

class EditableTableColumn<T, V>(columnName: String, typeClazz: Class<out V>, rowToCellDataMapper: (rowData: T) -> V, val valueChangeListener: (rowData: T, newCellData: V) -> Unit)
  : TableColumn<T, V>(columnName, typeClazz, rowToCellDataMapper)
