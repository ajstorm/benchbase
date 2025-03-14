/*
 * Copyright 2020 by OLTPBenchmark Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.:w
 *
 */

package com.oltpbenchmark.benchmarks.tpcc.procedures;

import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.tpcc.TPCCConfig;
import com.oltpbenchmark.benchmarks.tpcc.TPCCConstants;
import com.oltpbenchmark.benchmarks.tpcc.TPCCUtil;
import com.oltpbenchmark.benchmarks.tpcc.TPCCWorker;
import com.oltpbenchmark.benchmarks.tpcc.pojo.Stock;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewOrder extends TPCCProcedure {

  private static final Logger LOG = LoggerFactory.getLogger(NewOrder.class);

  public final SQLStmt stmtGetCustSQL =
      new SQLStmt(
          """
        SELECT C_DISCOUNT, C_LAST, C_CREDIT
          FROM %s
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public final SQLStmt stmtGetWhseSQL =
      new SQLStmt(
          """
        SELECT W_TAX
          FROM %s
         WHERE W_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_WAREHOUSE));

  public final SQLStmt stmtGetDistSQL =
      new SQLStmt(
          """
        SELECT D_NEXT_O_ID, D_TAX
          FROM %s
         WHERE D_W_ID = ? AND D_ID = ? FOR UPDATE
    """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public final SQLStmt stmtInsertNewOrderSQL =
      new SQLStmt(
          """
        INSERT INTO %s
         (NO_O_ID, NO_D_ID, NO_W_ID)
         VALUES ( ?, ?, ?)
    """
              .formatted(TPCCConstants.TABLENAME_NEWORDER));

  public final SQLStmt stmtUpdateDistReturningSQLPostgres =
      new SQLStmt(
          """
          UPDATE %s
             SET D_NEXT_O_ID = D_NEXT_O_ID + 1
           WHERE D_W_ID = ?
             AND D_ID = ?
           RETURNING D_TAX, D_NEXT_O_ID
          """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public final SQLStmt stmtUpdateDistReturningSQLOracle =
      new SQLStmt(
          """
          UPDATE %s
             SET D_NEXT_O_ID = D_NEXT_O_ID + 1
           WHERE D_W_ID = ?
             AND D_ID = ?
           RETURNING D_TAX, D_NEXT_O_ID
             INTO ?, ?
          """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public final SQLStmt stmtUpdateDistFallbackSQL =
      new SQLStmt(
          """
          UPDATE %s
             SET D_NEXT_O_ID = D_NEXT_O_ID + 1
           WHERE D_W_ID = ?
             AND D_ID = ?
          """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public final SQLStmt stmtSelectDistInfoSQL =
      new SQLStmt(
          """
          SELECT D_TAX, D_NEXT_O_ID
            FROM %s
           WHERE D_W_ID = ?
             AND D_ID = ?
          """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public final SQLStmt stmtInsertOOrderSQL =
      new SQLStmt(
          """
        INSERT INTO %s
         (O_ID, O_D_ID, O_W_ID, O_C_ID, O_ENTRY_D, O_OL_CNT, O_ALL_LOCAL)
         VALUES (?, ?, ?, ?, ?, ?, ?)
    """
              .formatted(TPCCConstants.TABLENAME_OPENORDER));

  public final SQLStmt stmtGetItemSQL =
      new SQLStmt(
          """
        SELECT I_ID, I_PRICE, I_NAME, I_DATA
          FROM %s
         WHERE I_ID IN (%s)
    """);

  public final SQLStmt stmtGetMultipleStocksSQL =
      new SQLStmt(
          """
        SELECT S_I_ID, S_W_ID, S_QUANTITY, S_DATA, S_DIST_01, S_DIST_02, S_DIST_03, S_DIST_04, S_DIST_05,
               S_DIST_06, S_DIST_07, S_DIST_08, S_DIST_09, S_DIST_10
          FROM %s
         WHERE (S_I_ID, S_W_ID) IN ((???))
         FOR UPDATE
    """
              .formatted(TPCCConstants.TABLENAME_STOCK));

  // SQL template for bulk stock update with dynamic generation based on the number of items
  private String generateBulkUpdateStockSQLPostgres(int numItems) {
    StringBuilder whenClauses = new StringBuilder();
    for (int i = 0; i < numItems; i++) {
      whenClauses.append("WHEN (?, ?) THEN ? ");
    }

    return String.format(
        """
        UPDATE %s
        SET S_QUANTITY = CASE (S_I_ID, S_W_ID) %s ELSE S_QUANTITY END,
            S_YTD = CASE (S_I_ID, S_W_ID) %s ELSE S_YTD END,
            S_ORDER_CNT = CASE (S_I_ID, S_W_ID) %s ELSE S_ORDER_CNT END,
            S_REMOTE_CNT = CASE (S_I_ID, S_W_ID) %s ELSE S_REMOTE_CNT END
        WHERE (S_I_ID, S_W_ID) IN (%s)
        """,
        TPCCConstants.TABLENAME_STOCK,
        whenClauses.toString(),
        whenClauses.toString(),
        whenClauses.toString(),
        whenClauses.toString(),
        generatePlaceholderPairs(numItems));
  }

  // SQL template for bulk stock update with dynamic generation based on the number of items
  private String generateBulkUpdateStockSQLOracle(int numItems) {
    StringBuilder whenClausesQuantity = new StringBuilder();
    StringBuilder whenClausesYtd = new StringBuilder();
    StringBuilder whenClausesOrderCnt = new StringBuilder();
    StringBuilder whenClausesRemoteCnt = new StringBuilder();

    for (int i = 0; i < numItems; i++) {
      // Use separate index placeholders for each condition
      int paramIndex1 = i * 3 + 1;
      int paramIndex2 = i * 3 + 2;
      int paramIndex3 = i * 3 + 3;

      // For S_QUANTITY
      whenClausesQuantity.append(
          String.format(
              "WHEN S_I_ID = ? AND S_W_ID = ? THEN ? ", paramIndex1, paramIndex2, paramIndex3));

      // For S_YTD
      paramIndex1 = numItems * 3 + i * 3 + 1;
      paramIndex2 = numItems * 3 + i * 3 + 2;
      paramIndex3 = numItems * 3 + i * 3 + 3;
      whenClausesYtd.append(
          String.format(
              "WHEN S_I_ID = ? AND S_W_ID = ? THEN ? ", paramIndex1, paramIndex2, paramIndex3));

      // For S_ORDER_CNT
      paramIndex1 = numItems * 6 + i * 3 + 1;
      paramIndex2 = numItems * 6 + i * 3 + 2;
      paramIndex3 = numItems * 6 + i * 3 + 3;
      whenClausesOrderCnt.append(
          String.format(
              "WHEN S_I_ID = ? AND S_W_ID = ? THEN ? ", paramIndex1, paramIndex2, paramIndex3));

      // For S_REMOTE_CNT
      paramIndex1 = numItems * 9 + i * 3 + 1;
      paramIndex2 = numItems * 9 + i * 3 + 2;
      paramIndex3 = numItems * 9 + i * 3 + 3;
      whenClausesRemoteCnt.append(
          String.format(
              "WHEN S_I_ID = ? AND S_W_ID = ? THEN ? ", paramIndex1, paramIndex2, paramIndex3));
    }

    return String.format(
        """
        UPDATE %s
        SET S_QUANTITY = CASE %s ELSE S_QUANTITY END,
            S_YTD = CASE %s ELSE S_YTD END,
            S_ORDER_CNT = CASE %s ELSE S_ORDER_CNT END,
            S_REMOTE_CNT = CASE %s ELSE S_REMOTE_CNT END
        WHERE (S_I_ID, S_W_ID) IN (%s)
        """,
        TPCCConstants.TABLENAME_STOCK,
        whenClausesQuantity.toString(),
        whenClausesYtd.toString(),
        whenClausesOrderCnt.toString(),
        whenClausesRemoteCnt.toString(),
        generatePlaceholderPairs(numItems));
  }

  // Helper method to generate placeholders for IN clause
  private String generatePlaceholderPairs(int count) {
    StringJoiner joiner = new StringJoiner(", ");
    for (int i = 0; i < count; i++) {
      joiner.add("(?, ?)");
    }
    return joiner.toString();
  }

  public final SQLStmt stmtInsertOrderLineSQL =
      new SQLStmt(
          """
        INSERT INTO %s
         (OL_O_ID, OL_D_ID, OL_W_ID, OL_NUMBER, OL_I_ID, OL_SUPPLY_W_ID, OL_QUANTITY, OL_AMOUNT, OL_DIST_INFO)
         VALUES (?,?,?,?,?,?,?,?,?)
    """
              .formatted(TPCCConstants.TABLENAME_ORDERLINE));

  public void run(
      Connection conn,
      Random gen,
      int terminalWarehouseID,
      int numWarehouses,
      int terminalDistrictLowerID,
      int terminalDistrictUpperID,
      TPCCWorker w)
      throws SQLException {

    int districtID = TPCCUtil.randomNumber(terminalDistrictLowerID, terminalDistrictUpperID, gen);
    int customerID = TPCCUtil.getCustomerID(gen);

    int numItems = TPCCUtil.randomNumber(5, 15, gen);
    int[] itemIDs = new int[numItems];
    int[] supplierWarehouseIDs = new int[numItems];
    int[] orderQuantities = new int[numItems];
    int allLocal = 1;

    for (int i = 0; i < numItems; i++) {
      itemIDs[i] = TPCCUtil.getItemID(gen);
      if (TPCCUtil.randomNumber(1, 100, gen) > 1) {
        supplierWarehouseIDs[i] = terminalWarehouseID;
      } else {
        do {
          supplierWarehouseIDs[i] = TPCCUtil.randomNumber(1, numWarehouses, gen);
        } while (supplierWarehouseIDs[i] == terminalWarehouseID && numWarehouses > 1);
        allLocal = 0;
      }
      orderQuantities[i] = TPCCUtil.randomNumber(1, 10, gen);
    }

    // we need to cause 1% of the new orders to be rolled back.
    if (TPCCUtil.randomNumber(1, 100, gen) == 1) {
      itemIDs[numItems - 1] = TPCCConfig.INVALID_ITEM_ID;
    }

    newOrderTransaction(
        terminalWarehouseID,
        districtID,
        customerID,
        numItems,
        allLocal,
        itemIDs,
        supplierWarehouseIDs,
        orderQuantities,
        conn);
  }

  private void newOrderTransaction(
      int w_id,
      int d_id,
      int c_id,
      int o_ol_cnt,
      int o_all_local,
      int[] itemIDs,
      int[] supplierWarehouseIDs,
      int[] orderQuantities,
      Connection conn)
      throws SQLException {

    getCustomer(conn, w_id, d_id, c_id);

    getWarehouse(conn, w_id);

    int d_next_o_id = getDistrict(conn, w_id, d_id);

    // Calculate total order amount and batch retrieve item prices
    float ol_amount = 0;

    DistrictInfo distInfo = updateDistrict(conn, w_id, d_id, ol_amount);

    insertOpenOrder(conn, w_id, d_id, c_id, o_ol_cnt, o_all_local, d_next_o_id);

    insertNewOrder(conn, w_id, d_id, d_next_o_id);

    // Fetch all item prices in a single query
    Map<Integer, Float> itemPrices = getItemPricesBatch(conn, itemIDs);

    for (int i = 0; i < o_ol_cnt; i++) {
      Float price = itemPrices.get(itemIDs[i]);
      if (price == null) {
        throw new UserAbortException(
            "EXPECTED new order rollback: I_ID=" + itemIDs[i] + " not found!");
      }
      ol_amount += orderQuantities[i] * price;
    }

    // Now get all stock information
    List<Stock> stockInfoList =
        getStockBatch(conn, supplierWarehouseIDs, itemIDs, orderQuantities, o_ol_cnt);

    // Insert all order lines
    insertOrderLines(
        conn,
        d_id,
        w_id,
        d_next_o_id,
        o_ol_cnt,
        itemIDs,
        supplierWarehouseIDs,
        orderQuantities,
        itemPrices,
        stockInfoList);

    // Update all stocks in a single batch operation
    updateStocksBatch(
        conn, w_id, o_ol_cnt, itemIDs, supplierWarehouseIDs, orderQuantities, stockInfoList);
  }

  private void insertOrderLines(
      Connection conn,
      int d_id,
      int w_id,
      int o_id,
      int o_ol_cnt,
      int[] itemIDs,
      int[] supplierWarehouseIDs,
      int[] orderQuantities,
      Map<Integer, Float> itemPrices,
      List<Stock> stockInfoList)
      throws SQLException {

    try (PreparedStatement stmtInsertOrderLine =
        this.getPreparedStatement(conn, stmtInsertOrderLineSQL)) {
      for (int ol_number = 1; ol_number <= o_ol_cnt; ol_number++) {
        int ol_supply_w_id = supplierWarehouseIDs[ol_number - 1];
        int ol_i_id = itemIDs[ol_number - 1];
        int ol_quantity = orderQuantities[ol_number - 1];
        float i_price = itemPrices.get(itemIDs[ol_number - 1]);
        Stock s = stockInfoList.get(ol_number - 1);

        float ol_amount = ol_quantity * i_price;
        String ol_dist_info = getDistInfo(d_id, s);

        stmtInsertOrderLine.setInt(1, o_id);
        stmtInsertOrderLine.setInt(2, d_id);
        stmtInsertOrderLine.setInt(3, w_id);
        stmtInsertOrderLine.setInt(4, ol_number);
        stmtInsertOrderLine.setInt(5, ol_i_id);
        stmtInsertOrderLine.setInt(6, ol_supply_w_id);
        stmtInsertOrderLine.setInt(7, ol_quantity);
        stmtInsertOrderLine.setDouble(8, ol_amount);
        stmtInsertOrderLine.setString(9, ol_dist_info);
        stmtInsertOrderLine.addBatch();
      }

      stmtInsertOrderLine.executeBatch();
      stmtInsertOrderLine.clearBatch();
    }
  }

  private void updateStocksBatch(
      Connection conn,
      int w_id,
      int o_ol_cnt,
      int[] itemIDs,
      int[] supplierWarehouseIDs,
      int[] orderQuantities,
      List<Stock> stockInfoList)
      throws SQLException {

    // Generate the bulk update SQL dynamically based on the number of items
    String bulkUpdateSQL;
    String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
    if (dbType.contains("postgresql")
        || dbType.contains("postgres")
        || dbType.contains("mysql")
        || dbType.contains("mariadb")
        || dbType.contains("db2")
        || dbType.contains("sql server")
        || dbType.contains("sqlite")) {
      bulkUpdateSQL = generateBulkUpdateStockSQLPostgres(o_ol_cnt); // Tuple comparison style
    }
    // Databases that require explicit condition style
    else if (dbType.contains("oracle")) {
      bulkUpdateSQL = generateBulkUpdateStockSQLOracle(o_ol_cnt); // Explicit condition style
    }
    // Unknown database
    else {
      // FIXME: this needs fixing
      throw new RuntimeException("Unknown database type");
    }
    try (PreparedStatement pstmt = conn.prepareStatement(bulkUpdateSQL)) {
      int paramIndex = 1;

      // Set the WHEN parameters for S_QUANTITY
      for (int i = 0; i < o_ol_cnt; i++) {
        pstmt.setInt(paramIndex++, itemIDs[i]);
        pstmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
        pstmt.setInt(paramIndex++, stockInfoList.get(i).s_quantity);
      }

      // Set the WHEN parameters for S_YTD
      for (int i = 0; i < o_ol_cnt; i++) {
        pstmt.setInt(paramIndex++, itemIDs[i]);
        pstmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
        pstmt.setFloat(paramIndex++, stockInfoList.get(i).s_ytd + orderQuantities[i]);
      }

      // Set the WHEN parameters for S_ORDER_CNT
      for (int i = 0; i < o_ol_cnt; i++) {
        pstmt.setInt(paramIndex++, itemIDs[i]);
        pstmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
        pstmt.setInt(paramIndex++, stockInfoList.get(i).s_order_cnt + 1);
      }

      // Set the WHEN parameters for S_REMOTE_CNT
      for (int i = 0; i < o_ol_cnt; i++) {
        int s_remote_cnt_increment = (supplierWarehouseIDs[i] == w_id) ? 0 : 1;
        pstmt.setInt(paramIndex++, itemIDs[i]);
        pstmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
        pstmt.setInt(paramIndex++, stockInfoList.get(i).s_remote_cnt + s_remote_cnt_increment);
      }

      // Set the IN clause parameters
      for (int i = 0; i < o_ol_cnt; i++) {
        pstmt.setInt(paramIndex++, itemIDs[i]);
        pstmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
      }

      pstmt.executeUpdate();
    }
  }

  private String getDistInfo(int d_id, Stock s) {
    return switch (d_id) {
      case 1 -> s.s_dist_01;
      case 2 -> s.s_dist_02;
      case 3 -> s.s_dist_03;
      case 4 -> s.s_dist_04;
      case 5 -> s.s_dist_05;
      case 6 -> s.s_dist_06;
      case 7 -> s.s_dist_07;
      case 8 -> s.s_dist_08;
      case 9 -> s.s_dist_09;
      case 10 -> s.s_dist_10;
      default -> null;
    };
  }

  private List<Stock> getStockBatch(
      Connection conn, int[] supplierWarehouseIDs, int[] itemIDs, int[] orderQuantities, int count)
      throws SQLException {

    // Create parameter pairs for the IN clause
    StringBuilder inClauseBuilder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        inClauseBuilder.append(", ");
      }
      inClauseBuilder.append("(?, ?)");
    }

    // Replace the placeholder in the SQL with the actual IN clause
    // FIXME: This should use the more conventional string building and not the
    // replacement of ???
    String sql = stmtGetMultipleStocksSQL.getSQL().replace("(???)", inClauseBuilder.toString());

    // Create a map to store results by (item_id, warehouse_id) for quick lookup
    Map<Pair<Integer, Integer>, Stock> stockMap = new HashMap<>();

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      // Set all parameters for the IN clause
      int paramIndex = 1;
      for (int i = 0; i < count; i++) {
        stmt.setInt(paramIndex++, itemIDs[i]);
        stmt.setInt(paramIndex++, supplierWarehouseIDs[i]);
      }

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          int s_i_id = rs.getInt("S_I_ID");
          int s_w_id = rs.getInt("S_W_ID");

          Stock s = new Stock();
          s.s_quantity = rs.getInt("S_QUANTITY");
          s.s_dist_01 = rs.getString("S_DIST_01");
          s.s_dist_02 = rs.getString("S_DIST_02");
          s.s_dist_03 = rs.getString("S_DIST_03");
          s.s_dist_04 = rs.getString("S_DIST_04");
          s.s_dist_05 = rs.getString("S_DIST_05");
          s.s_dist_06 = rs.getString("S_DIST_06");
          s.s_dist_07 = rs.getString("S_DIST_07");
          s.s_dist_08 = rs.getString("S_DIST_08");
          s.s_dist_09 = rs.getString("S_DIST_09");
          s.s_dist_10 = rs.getString("S_DIST_10");

          // Initialize tracking properties
          s.s_ytd = 0;
          s.s_order_cnt = 0;
          s.s_remote_cnt = 0;

          stockMap.put(new Pair<>(s_i_id, s_w_id), s);
        }
      }
    }

    // Construct result list and apply quantity calculations
    List<Stock> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Pair<Integer, Integer> key = new Pair<>(itemIDs[i], supplierWarehouseIDs[i]);
      Stock s = stockMap.get(key);

      if (s == null) {
        throw new RuntimeException("S_I_ID=" + itemIDs[i] + " not found!");
      }

      // Create a copy of the stock to avoid modifying the one in the map
      // (in case there are duplicate items in different positions)
      Stock stockCopy = new Stock();
      copyStockProperties(s, stockCopy);

      // Calculate new quantity
      int ol_quantity = orderQuantities[i];
      if (stockCopy.s_quantity - ol_quantity >= 10) {
        stockCopy.s_quantity -= ol_quantity;
      } else {
        stockCopy.s_quantity += -ol_quantity + 91;
      }

      result.add(stockCopy);
    }

    return result;
  }

  // Helper method to copy Stock properties
  private void copyStockProperties(Stock source, Stock target) {
    target.s_quantity = source.s_quantity;
    target.s_dist_01 = source.s_dist_01;
    target.s_dist_02 = source.s_dist_02;
    target.s_dist_03 = source.s_dist_03;
    target.s_dist_04 = source.s_dist_04;
    target.s_dist_05 = source.s_dist_05;
    target.s_dist_06 = source.s_dist_06;
    target.s_dist_07 = source.s_dist_07;
    target.s_dist_08 = source.s_dist_08;
    target.s_dist_09 = source.s_dist_09;
    target.s_dist_10 = source.s_dist_10;
    target.s_ytd = source.s_ytd;
    target.s_order_cnt = source.s_order_cnt;
    target.s_remote_cnt = source.s_remote_cnt;
  }

  // Helper class for composite key in the map
  private static class Pair<K, V> {
    private final K first;
    private final V second;

    public Pair(K first, V second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Pair<?, ?> pair = (Pair<?, ?>) o;
      return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second);
    }
  }

  private Map<Integer, Float> getItemPricesBatch(Connection conn, int[] itemIDs)
      throws SQLException {
    String placeholders =
        Arrays.stream(itemIDs).mapToObj(id -> "?").collect(Collectors.joining(", "));

    String sql = String.format(stmtGetItemSQL.getSQL(), TPCCConstants.TABLENAME_ITEM, placeholders);

    Map<Integer, Float> prices = new HashMap<>();

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      for (int i = 0; i < itemIDs.length; i++) {
        stmt.setInt(i + 1, itemIDs[i]);
      }

      try (ResultSet rs = stmt.executeQuery()) {
        int rowsReturned = 0;
        while (rs.next()) {
          rowsReturned++;
          prices.put(rs.getInt("I_ID"), rs.getFloat("I_PRICE"));
        }
        if (rowsReturned != itemIDs.length) {
          // There are two reasons we may get into this branch:
          //    1) We chose duplicate items in the transaction
          //    2) With 1% probability, we chose the invalid item id
          //
          // The first case is not a concern, and we can proceed normally. The
          // second case must drive a rollback, as specified in 2.4.2.2 of the
          // specification.
          if (itemIDs[itemIDs.length - 1] == TPCCConfig.INVALID_ITEM_ID) {
            throw new UserAbortException("Expected rollback of NewOrder transaction");
          }
        }
      }
    }

    return prices;
  }

  private void insertNewOrder(Connection conn, int w_id, int d_id, int o_id) throws SQLException {
    try (PreparedStatement stmtInsertNewOrder =
        this.getPreparedStatement(conn, stmtInsertNewOrderSQL); ) {
      stmtInsertNewOrder.setInt(1, o_id);
      stmtInsertNewOrder.setInt(2, d_id);
      stmtInsertNewOrder.setInt(3, w_id);
      int result = stmtInsertNewOrder.executeUpdate();

      if (result == 0) {
        LOG.warn("new order not inserted");
      }
    }
  }

  private void insertOpenOrder(
      Connection conn, int w_id, int d_id, int c_id, int o_ol_cnt, int o_all_local, int o_id)
      throws SQLException {
    try (PreparedStatement stmtInsertOOrder =
        this.getPreparedStatement(conn, stmtInsertOOrderSQL); ) {
      stmtInsertOOrder.setInt(1, o_id);
      stmtInsertOOrder.setInt(2, d_id);
      stmtInsertOOrder.setInt(3, w_id);
      stmtInsertOOrder.setInt(4, c_id);
      stmtInsertOOrder.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
      stmtInsertOOrder.setInt(6, o_ol_cnt);
      stmtInsertOOrder.setInt(7, o_all_local);

      int result = stmtInsertOOrder.executeUpdate();

      if (result == 0) {
        LOG.warn("open order not inserted");
      }
    }
  }

  private static class DistrictInfo {
    String d_name;
    String d_street_1;
    String d_street_2;
    String d_city;
    String d_state;
    String d_zip;
    Float d_tax;
    Integer d_next_o_id;
  }

  private DistrictInfo updateDistrict(Connection conn, int w_id, int d_id, float ol_amount)
      throws SQLException {
    DistrictInfo distInfo = new DistrictInfo();

    boolean supportsReturning = TPCCUtil.checkIfDatabaseSupportsReturning(conn);

    if (supportsReturning) {
      String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
      if (dbType.contains("postgresql") || dbType.contains("postgres")) {
        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, stmtUpdateDistReturningSQLPostgres)) {
          stmt.setInt(1, w_id);
          stmt.setInt(2, d_id);

          try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
              throw new RuntimeException(
                  "Error!! Cannot update next_order_id on district for D_ID="
                      + d_id
                      + " D_W_ID="
                      + w_id);
            }

            distInfo.d_tax = rs.getFloat("D_TAX");
            distInfo.d_next_o_id = rs.getInt("D_NEXT_O_ID");
          }
        }
      } else if (dbType.contains("oracle")) {
        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, stmtUpdateDistReturningSQLOracle)) {
          // Check if it's actually an OraclePreparedStatement
          Class<?> oraclePstmtClass = Class.forName("oracle.jdbc.OraclePreparedStatement");
          if (oraclePstmtClass.isInstance(stmt)) {
            stmt.setInt(1, w_id);
            stmt.setInt(2, d_id);

            Method registerReturnParameter =
                oraclePstmtClass.getMethod("registerReturnParameter", int.class, int.class);

            registerReturnParameter.invoke(stmt, 3, Types.FLOAT);
            registerReturnParameter.invoke(stmt, 4, Types.FLOAT);

            // Execute the update statement with RETURNING clause
            stmt.executeUpdate();

            // Retrieve the returned values via a ResultSet using reflection
            Method getReturnResultSet = oraclePstmtClass.getMethod("getReturnResultSet");
            ResultSet rs = (ResultSet) getReturnResultSet.invoke(stmt);

            if (rs.next()) {
              distInfo.d_tax = rs.getFloat(1);
              distInfo.d_next_o_id = rs.getInt(2);
            }
          } else {
            throw new SQLException("Statement is not an OraclePreparedStatement");
          }
        } catch (ClassNotFoundException
            | NoSuchMethodException
            | IllegalAccessException
            | InvocationTargetException e) {
          // FIXME: do something more elegant here.
          throw new RuntimeException("Invalid Oracle Setup", e);
        }
      } else {
        throw new RuntimeException("RETURNING support only exists for PG and Oracle");
      }

    } else {
      // Perform update without RETURNING
      try (PreparedStatement stmtUpdate =
          this.getPreparedStatement(conn, stmtUpdateDistFallbackSQL)) {
        stmtUpdate.setInt(1, w_id);
        stmtUpdate.setInt(2, d_id);

        int updated = stmtUpdate.executeUpdate();
        if (updated == 0) {
          throw new RuntimeException(
              "Error!! Cannot update next_order_id on district for D_ID="
                  + d_id
                  + " D_W_ID="
                  + w_id);
        }
      }

      // Retrieve values separately
      try (PreparedStatement stmtSelect = this.getPreparedStatement(conn, stmtSelectDistInfoSQL)) {
        stmtSelect.setInt(1, w_id);
        stmtSelect.setInt(2, d_id);

        try (ResultSet rs = stmtSelect.executeQuery()) {
          if (!rs.next()) {
            throw new RuntimeException(
                "Error!! Cannot retrieve district info for D_ID=" + d_id + " D_W_ID=" + w_id);
          }

          distInfo.d_tax = rs.getFloat("D_TAX");
          distInfo.d_next_o_id = rs.getInt("D_NEXT_O_ID");
        }
      }
    }

    return distInfo;
  }

  private int getDistrict(Connection conn, int w_id, int d_id) throws SQLException {
    try (PreparedStatement stmtGetDist = this.getPreparedStatement(conn, stmtGetDistSQL)) {
      stmtGetDist.setInt(1, w_id);
      stmtGetDist.setInt(2, d_id);
      try (ResultSet rs = stmtGetDist.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("D_ID=" + d_id + " D_W_ID=" + w_id + " not found!");
        }
        return rs.getInt("D_NEXT_O_ID");
      }
    }
  }

  private void getWarehouse(Connection conn, int w_id) throws SQLException {
    try (PreparedStatement stmtGetWhse = this.getPreparedStatement(conn, stmtGetWhseSQL)) {
      stmtGetWhse.setInt(1, w_id);
      try (ResultSet rs = stmtGetWhse.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("W_ID=" + w_id + " not found!");
        }
      }
    }
  }

  private void getCustomer(Connection conn, int w_id, int d_id, int c_id) throws SQLException {
    try (PreparedStatement stmtGetCust = this.getPreparedStatement(conn, stmtGetCustSQL)) {
      stmtGetCust.setInt(1, w_id);
      stmtGetCust.setInt(2, d_id);
      stmtGetCust.setInt(3, c_id);
      try (ResultSet rs = stmtGetCust.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("C_D_ID=" + d_id + " C_ID=" + c_id + " not found!");
        }
      }
    }
  }
}
