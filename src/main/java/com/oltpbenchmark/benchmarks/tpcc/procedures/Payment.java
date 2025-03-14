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
 * limitations under the License.
 *
 */

package com.oltpbenchmark.benchmarks.tpcc.procedures;

import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.tpcc.TPCCConfig;
import com.oltpbenchmark.benchmarks.tpcc.TPCCConstants;
import com.oltpbenchmark.benchmarks.tpcc.TPCCUtil;
import com.oltpbenchmark.benchmarks.tpcc.TPCCWorker;
import com.oltpbenchmark.benchmarks.tpcc.pojo.Customer;
import com.oltpbenchmark.benchmarks.tpcc.pojo.District;
import com.oltpbenchmark.benchmarks.tpcc.pojo.Warehouse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Payment extends TPCCProcedure {

  private static final Logger LOG = LoggerFactory.getLogger(Payment.class);

  public SQLStmt payUpdateWhseSQL =
      new SQLStmt(
          """
        UPDATE %s
           SET W_YTD = W_YTD + ?
         WHERE W_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_WAREHOUSE));

  public SQLStmt payGetWhseSQL =
      new SQLStmt(
          """
        SELECT W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP, W_NAME
          FROM %s
         WHERE W_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_WAREHOUSE));

  // SQL statement for warehouse update with RETURNING
  public SQLStmt payUpdateWhseReturningSQLPostgres =
      new SQLStmt(
          """
        UPDATE %s
           SET W_YTD = W_YTD + ?
         WHERE W_ID = ?
         RETURNING W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP, W_NAME
    """
              .formatted(TPCCConstants.TABLENAME_WAREHOUSE));

  public SQLStmt payUpdateWhseReturningSQLOracle =
      new SQLStmt(
          """
        UPDATE %s
           SET W_YTD = W_YTD + ?
         WHERE W_ID = ?
         RETURNING W_STREET_1, W_STREET_2, W_CITY, W_STATE, W_ZIP, W_NAME
           INTO ?, ?, ?, ?, ?, ?
    """
              .formatted(TPCCConstants.TABLENAME_WAREHOUSE));

  public SQLStmt payUpdateDistSQLPostgres =
      new SQLStmt(
          """
        UPDATE %s
           SET D_YTD = D_YTD + ?
         WHERE D_W_ID = ?
           AND D_ID = ?
         RETURNING D_STREET_1, D_STREET_2, D_CITY, D_STATE, D_ZIP, D_NAME
    """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public SQLStmt payUpdateDistSQLOracle =
      new SQLStmt(
          """
        UPDATE %s
           SET D_YTD = D_YTD + ?
         WHERE D_W_ID = ?
           AND D_ID = ?
         RETURNING D_STREET_1, D_STREET_2, D_CITY, D_STATE, D_ZIP, D_NAME
           INTO ?, ?, ?, ?, ?, ?
    """
              .formatted(TPCCConstants.TABLENAME_DISTRICT));

  public SQLStmt payGetCustSQL =
      new SQLStmt(
          """
        SELECT C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2,
               C_CITY, C_STATE, C_ZIP, C_PHONE, C_CREDIT, C_CREDIT_LIM,
               C_DISCOUNT, C_BALANCE, C_YTD_PAYMENT, C_PAYMENT_CNT, C_SINCE
          FROM %s
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public SQLStmt payGetCustCdataSQL =
      new SQLStmt(
          """
        SELECT C_DATA
          FROM %s
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public SQLStmt payUpdateCustBalCdataSQL =
      new SQLStmt(
          """
        UPDATE %s
           SET C_BALANCE = ?,
               C_YTD_PAYMENT = ?,
               C_PAYMENT_CNT = ?,
               C_DATA = ?
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public SQLStmt payUpdateCustBalSQL =
      new SQLStmt(
          """
        UPDATE %s
           SET C_BALANCE = ?,
               C_YTD_PAYMENT = ?,
               C_PAYMENT_CNT = ?
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_ID = ?
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public SQLStmt payUpdateCustReturningSQLPostgres =
      new SQLStmt(
          """
      UPDATE %s
         SET C_BALANCE = C_BALANCE - ?,
             C_YTD_PAYMENT = C_YTD_PAYMENT + ?,
             C_PAYMENT_CNT = C_PAYMENT_CNT + 1,
             C_DATA = CASE C_CREDIT
                        WHEN 'BC' THEN SUBSTRING(? || C_DATA, 1, 500)
                        ELSE C_DATA
                      END
       WHERE C_W_ID = ?
         AND C_D_ID = ?
         AND C_ID = ?
       RETURNING C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2,
                C_CITY, C_STATE, C_ZIP, C_PHONE, C_SINCE, C_CREDIT,
                C_CREDIT_LIM, C_DISCOUNT, C_BALANCE, C_DATA
  """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  //  public SQLStmt payUpdateCustReturningSQLOracle =
  //      new SQLStmt(
  //          """
  //      UPDATE %s
  //         SET C_BALANCE = C_BALANCE - ?,
  //             C_YTD_PAYMENT = C_YTD_PAYMENT + ?,
  //             C_PAYMENT_CNT = C_PAYMENT_CNT + 1,
  //             C_DATA = CASE C_CREDIT
  //                        WHEN 'BC' THEN CAST(SUBSTRING(? || C_DATA, 1, 500) AS VARCHAR2(500))
  //                        ELSE C_DATA
  //                      END
  //       WHERE C_W_ID = ?
  //         AND C_D_ID = ?
  //         AND C_ID = ?
  //       RETURNING C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2,
  //                C_CITY, C_STATE, C_ZIP, C_PHONE, C_SINCE, C_CREDIT,
  //                C_CREDIT_LIM, C_DISCOUNT, C_BALANCE,
  //                CASE C_CREDIT WHEN 'BC' THEN C_DATA ELSE '' END AS C_DATA
  //           INTO ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
  //  """
  //              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  // FIXME: Oracle doesn't support case statements in returning clauses
  public SQLStmt payUpdateCustReturningSQLOracle =
      new SQLStmt(
          """
      UPDATE %s
         SET C_BALANCE = C_BALANCE - ?,
             C_YTD_PAYMENT = C_YTD_PAYMENT + ?,
             C_PAYMENT_CNT = C_PAYMENT_CNT + 1,
             C_DATA = CASE C_CREDIT
                       WHEN 'BC' THEN SUBSTR(? || C_DATA, 1, 500)
                       ELSE C_DATA
                     END
       WHERE C_W_ID = ?
         AND C_D_ID = ?
         AND C_ID = ?
       RETURNING C_FIRST, C_MIDDLE, C_LAST, C_STREET_1, C_STREET_2,
                C_CITY, C_STATE, C_ZIP, C_PHONE, C_SINCE, C_CREDIT,
                C_CREDIT_LIM, C_DISCOUNT, C_BALANCE, C_DATA
           INTO ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
  """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public SQLStmt payInsertHistSQL =
      new SQLStmt(
          """
        INSERT INTO %s
         (H_C_D_ID, H_C_W_ID, H_C_ID, H_D_ID, H_W_ID, H_DATE, H_AMOUNT, H_DATA)
         VALUES (?,?,?,?,?,?,?,?)
    """
              .formatted(TPCCConstants.TABLENAME_HISTORY));

  public SQLStmt customerByNameSQL =
      new SQLStmt(
          """
        SELECT C_FIRST, C_MIDDLE, C_ID, C_STREET_1, C_STREET_2, C_CITY,
               C_STATE, C_ZIP, C_PHONE, C_CREDIT, C_CREDIT_LIM, C_DISCOUNT,
               C_BALANCE, C_YTD_PAYMENT, C_PAYMENT_CNT, C_SINCE
          FROM %s
         WHERE C_W_ID = ?
           AND C_D_ID = ?
           AND C_LAST = ?
         ORDER BY C_FIRST
    """
              .formatted(TPCCConstants.TABLENAME_CUSTOMER));

  public void run(
      Connection conn,
      Random gen,
      int w_id,
      int numWarehouses,
      int terminalDistrictLowerID,
      int terminalDistrictUpperID,
      TPCCWorker worker)
      throws SQLException {

    int districtID = TPCCUtil.randomNumber(terminalDistrictLowerID, terminalDistrictUpperID, gen);

    float paymentAmount = (float) (TPCCUtil.randomNumber(100, 500000, gen) / 100.0);

    Warehouse w = updateAndGetWarehouse(conn, w_id, paymentAmount);

    District d = updateAndGetDistrict(conn, w_id, districtID, paymentAmount);

    int x = TPCCUtil.randomNumber(1, 100, gen);

    int customerDistrictID = getCustomerDistrictId(gen, districtID, x);
    int customerWarehouseID = getCustomerWarehouseID(gen, w_id, numWarehouses, x);

    // Use the optimized method instead of the separate update calls
    Customer c =
        updateAndGetCustomer(
            conn, gen, customerWarehouseID, customerDistrictID, paymentAmount, w_id, districtID);

    insertHistory(
        conn,
        w_id,
        districtID,
        customerDistrictID,
        customerWarehouseID,
        paymentAmount,
        w.w_name,
        d.d_name,
        c);

    if (LOG.isTraceEnabled()) {
      StringBuilder terminalMessage = new StringBuilder();
      terminalMessage.append(
          "\n+---------------------------- PAYMENT ----------------------------+");
      terminalMessage.append("\n Date: ").append(TPCCUtil.getCurrentTime());
      terminalMessage.append("\n\n Warehouse: ");
      terminalMessage.append(w_id);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(w.w_street_1);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(w.w_street_2);
      terminalMessage.append("\n   City:    ");
      terminalMessage.append(w.w_city);
      terminalMessage.append("   State: ");
      terminalMessage.append(w.w_state);
      terminalMessage.append("  Zip: ");
      terminalMessage.append(w.w_zip);
      terminalMessage.append("\n\n District:  ");
      terminalMessage.append(districtID);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(d.d_street_1);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(d.d_street_2);
      terminalMessage.append("\n   City:    ");
      terminalMessage.append(d.d_city);
      terminalMessage.append("   State: ");
      terminalMessage.append(d.d_state);
      terminalMessage.append("  Zip: ");
      terminalMessage.append(d.d_zip);
      terminalMessage.append("\n\n Customer:  ");
      terminalMessage.append(c.c_id);
      terminalMessage.append("\n   Name:    ");
      terminalMessage.append(c.c_first);
      terminalMessage.append(" ");
      terminalMessage.append(c.c_middle);
      terminalMessage.append(" ");
      terminalMessage.append(c.c_last);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(c.c_street_1);
      terminalMessage.append("\n   Street:  ");
      terminalMessage.append(c.c_street_2);
      terminalMessage.append("\n   City:    ");
      terminalMessage.append(c.c_city);
      terminalMessage.append("   State: ");
      terminalMessage.append(c.c_state);
      terminalMessage.append("  Zip: ");
      terminalMessage.append(c.c_zip);
      terminalMessage.append("\n   Since:   ");
      if (c.c_since != null) {
        terminalMessage.append(c.c_since.toString());
      } else {
        terminalMessage.append("");
      }
      terminalMessage.append("\n   Credit:  ");
      terminalMessage.append(c.c_credit);
      terminalMessage.append("\n   %Disc:   ");
      terminalMessage.append(c.c_discount);
      terminalMessage.append("\n   Phone:   ");
      terminalMessage.append(c.c_phone);
      terminalMessage.append("\n\n Amount Paid:      ");
      terminalMessage.append(paymentAmount);
      terminalMessage.append("\n Credit Limit:     ");
      terminalMessage.append(c.c_credit_lim);
      terminalMessage.append("\n New Cust-Balance: ");
      terminalMessage.append(c.c_balance);
      if (c.c_credit.equals("BC")) {
        if (c.c_data.length() > 50) {
          terminalMessage.append("\n\n Cust-Data: ").append(c.c_data.substring(0, 50));
          int data_chunks = c.c_data.length() > 200 ? 4 : c.c_data.length() / 50;
          for (int n = 1; n < data_chunks; n++) {
            terminalMessage
                .append("\n            ")
                .append(c.c_data.substring(n * 50, (n + 1) * 50));
          }
        } else {
          terminalMessage.append("\n\n Cust-Data: ").append(c.c_data);
        }
      }
      terminalMessage.append(
          "\n+-----------------------------------------------------------------+\n\n");

      LOG.trace(terminalMessage.toString());
    }
  }

  private int getCustomerWarehouseID(Random gen, int w_id, int numWarehouses, int x) {
    int customerWarehouseID;
    if (x <= 85) {
      customerWarehouseID = w_id;
    } else {
      do {
        customerWarehouseID = TPCCUtil.randomNumber(1, numWarehouses, gen);
      } while (customerWarehouseID == w_id && numWarehouses > 1);
    }
    return customerWarehouseID;
  }

  private int getCustomerDistrictId(Random gen, int districtID, int x) {
    if (x <= 85) {
      return districtID;
    } else {
      return TPCCUtil.randomNumber(1, TPCCConfig.configDistPerWhse, gen);
    }
  }

  private Warehouse updateAndGetWarehouse(Connection conn, int w_id, float paymentAmount)
      throws SQLException {
    Warehouse w = new Warehouse();

    // Check if database supports RETURNING clause
    boolean supportsReturning = TPCCUtil.checkIfDatabaseSupportsReturning(conn);

    if (supportsReturning) {
      String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();

      if (dbType.contains("postgresql") || dbType.contains("postgres")) {
        // Use UPDATE with RETURNING clause to do both operations in one statement
        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, payUpdateWhseReturningSQLPostgres)) {
          stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
          stmt.setInt(2, w_id);

          try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
              throw new RuntimeException("W_ID=" + w_id + " not found!");
            }

            w.w_street_1 = rs.getString("W_STREET_1");
            w.w_street_2 = rs.getString("W_STREET_2");
            w.w_city = rs.getString("W_CITY");
            w.w_state = rs.getString("W_STATE");
            w.w_zip = rs.getString("W_ZIP");
            w.w_name = rs.getString("W_NAME");
          }
        }
      } else if (dbType.contains("oracle")) {
        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, payUpdateWhseReturningSQLOracle)) {
          // Check if it's actually an OraclePreparedStatement
          Class<?> oraclePstmtClass = Class.forName("oracle.jdbc.OraclePreparedStatement");
          if (oraclePstmtClass.isInstance(stmt)) {
            // Set input parameters
            stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
            stmt.setInt(2, w_id);

            // Register output parameters using reflection
            Method registerReturnParameter =
                oraclePstmtClass.getMethod("registerReturnParameter", int.class, int.class);
            for (int i = 3; i <= 8; i++) {
              registerReturnParameter.invoke(stmt, i, Types.VARCHAR);
            }

            // Execute and get results
            stmt.executeUpdate();

            // Get result set via reflection
            Method getReturnResultSet = oraclePstmtClass.getMethod("getReturnResultSet");
            ResultSet rs = (ResultSet) getReturnResultSet.invoke(stmt);

            if (rs.next()) {
              w.w_street_1 = rs.getString(1);
              w.w_street_2 = rs.getString(2);
              w.w_city = rs.getString(3);
              w.w_state = rs.getString(4);
              w.w_zip = rs.getString(5);
              w.w_name = rs.getString(6);
            }
          } else {
            // Fall back to CallableStatement approach if not OraclePreparedStatement
            LOG.warn("Falling back to Oracle slow path");
            updateWarehouse(conn, w_id, paymentAmount);
            w = getWarehouse(conn, w_id);
          }
        } catch (ClassNotFoundException
            | NoSuchMethodException
            | IllegalAccessException
            | InvocationTargetException e) {
          // Oracle JDBC driver not available or reflection failed
          // Fall back to CallableStatement approach
          updateWarehouse(conn, w_id, paymentAmount);
          w = getWarehouse(conn, w_id);
        }
      } else {
        throw new RuntimeException("Unsupported vendor for RETURNING");
      }
    } else {
      // For databases that don't support RETURNING, use the original two-step approach
      updateWarehouse(conn, w_id, paymentAmount);
      w = getWarehouse(conn, w_id);
    }

    return w;
  }

  // Keep the original methods for backward compatibility or when needed separately
  private void updateWarehouse(Connection conn, int w_id, float paymentAmount) throws SQLException {
    try (PreparedStatement payUpdateWhse = this.getPreparedStatement(conn, payUpdateWhseSQL)) {
      payUpdateWhse.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
      payUpdateWhse.setInt(2, w_id);
      int result = payUpdateWhse.executeUpdate();
      if (result == 0) {
        throw new RuntimeException("W_ID=" + w_id + " not found!");
      }
    }
  }

  private Warehouse getWarehouse(Connection conn, int w_id) throws SQLException {
    try (PreparedStatement payGetWhse = this.getPreparedStatement(conn, payGetWhseSQL)) {
      payGetWhse.setInt(1, w_id);

      try (ResultSet rs = payGetWhse.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException("W_ID=" + w_id + " not found!");
        }

        Warehouse w = new Warehouse();
        w.w_street_1 = rs.getString("W_STREET_1");
        w.w_street_2 = rs.getString("W_STREET_2");
        w.w_city = rs.getString("W_CITY");
        w.w_state = rs.getString("W_STATE");
        w.w_zip = rs.getString("W_ZIP");
        w.w_name = rs.getString("W_NAME");

        return w;
      }
    }
  }

  private Customer updateAndGetCustomer(
      Connection conn,
      Random gen,
      int customerWarehouseID,
      int customerDistrictID,
      float paymentAmount,
      int w_id,
      int districtID)
      throws SQLException {

    // Check if database supports RETURNING clause
    boolean supportsReturning = TPCCUtil.checkIfDatabaseSupportsReturning(conn);

    // Get customer, either by name, or by ID. In the supportsReturning case,
    // this will just generate a customer ID.
    Customer c = getCustomer(conn, gen, customerDistrictID, customerWarehouseID, paymentAmount);
    if (supportsReturning) {
      String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();

      // Construct the c_data prefix for BC customers - will be used if customer has bad credit
      String dataPrefix =
          c.c_id
              + " "
              + customerDistrictID
              + " "
              + customerWarehouseID
              + " "
              + districtID
              + " "
              + w_id
              + " "
              + paymentAmount
              + " | ";

      if (dbType.contains("postgresql") || dbType.contains("postgres")) {

        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, payUpdateCustReturningSQLPostgres)) {
          stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
          stmt.setFloat(2, paymentAmount);
          stmt.setString(3, dataPrefix);
          stmt.setInt(4, customerWarehouseID);
          stmt.setInt(5, customerDistrictID);
          stmt.setInt(6, c.c_id);

          try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
              throw new RuntimeException(
                  "C_ID="
                      + c.c_id
                      + " C_W_ID="
                      + customerWarehouseID
                      + " C_D_ID="
                      + customerDistrictID
                      + " not found!");
            }

            // Update the customer object with returned values
            c.c_first = rs.getString("C_FIRST");
            c.c_middle = rs.getString("C_MIDDLE");
            c.c_last = rs.getString("C_LAST");
            c.c_street_1 = rs.getString("C_STREET_1");
            c.c_street_2 = rs.getString("C_STREET_2");
            c.c_city = rs.getString("C_CITY");
            c.c_state = rs.getString("C_STATE");
            c.c_zip = rs.getString("C_ZIP");
            c.c_phone = rs.getString("C_PHONE");
            c.c_since = rs.getTimestamp("C_SINCE");
            c.c_credit = rs.getString("C_CREDIT");
            c.c_credit_lim = rs.getFloat("C_CREDIT_LIM");
            c.c_discount = rs.getFloat("C_DISCOUNT");
            c.c_balance = rs.getFloat("C_BALANCE");
            c.c_data = rs.getString("C_DATA");
          }
        }
      } else if (dbType.contains("oracle")) {
        try (PreparedStatement stmt =
            this.getPreparedStatement(conn, payUpdateCustReturningSQLOracle)) {
          // Check if it's actually an OraclePreparedStatement
          Class<?> oraclePstmtClass = Class.forName("oracle.jdbc.OraclePreparedStatement");
          if (oraclePstmtClass.isInstance(stmt)) {
            // Set input parameters (positions 1–6)
            stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
            stmt.setFloat(2, paymentAmount);
            stmt.setString(3, dataPrefix);
            stmt.setInt(4, customerWarehouseID);
            stmt.setInt(5, customerDistrictID);
            stmt.setInt(6, c.c_id);

            Method registerReturnParameter =
                oraclePstmtClass.getMethod("registerReturnParameter", int.class, int.class);

            // Register columns 7-9 as VARCHAR
            for (int i = 7; i <= 15; i++) {
              registerReturnParameter.invoke(stmt, i, Types.VARCHAR);
            }
            // Register column 16 as TIMESTAMP
            registerReturnParameter.invoke(stmt, 16, Types.TIMESTAMP);
            // Register column 17 as VARCHAR
            registerReturnParameter.invoke(stmt, 17, Types.VARCHAR);
            // Register columns 18-20 as FLOAT
            for (int i = 18; i <= 20; i++) {
              registerReturnParameter.invoke(stmt, i, Types.FLOAT);
            }
            // Register column 21 as VARCHAR
            registerReturnParameter.invoke(stmt, 21, Types.VARCHAR);

            // Execute the update statement with RETURNING clause
            stmt.executeUpdate();

            // Retrieve the returned values via a ResultSet using reflection
            Method getReturnResultSet = oraclePstmtClass.getMethod("getReturnResultSet");
            ResultSet rs = (ResultSet) getReturnResultSet.invoke(stmt);

            if (rs.next()) {
              c.c_first = rs.getString(1);
              c.c_middle = rs.getString(2);
              c.c_last = rs.getString(3);
              c.c_street_1 = rs.getString(4);
              c.c_street_2 = rs.getString(5);
              c.c_city = rs.getString(6);
              c.c_state = rs.getString(7);
              c.c_zip = rs.getString(8);
              c.c_phone = rs.getString(9);
              c.c_since = rs.getTimestamp(10);
              c.c_credit = rs.getString(11);
              c.c_credit_lim = rs.getFloat(12);
              c.c_discount = rs.getFloat(13);
              c.c_balance = rs.getFloat(14);
              c.c_data = rs.getString(15);
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

        //        try (CallableStatement stmt =
        // conn.prepareCall(payUpdateCustReturningSQLOracle.getSQL())) {
        //          // Set input parameters (positions 1-6 in this example)
        //          stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
        //          stmt.setFloat(2, paymentAmount);
        //          stmt.setString(3, dataPrefix);
        //          stmt.setInt(4, customerWarehouseID);
        //          stmt.setInt(5, customerDistrictID);
        //          stmt.setInt(6, c.c_id);
        //
        //          // Register output parameters (positions 7-21)
        //          stmt.registerOutParameter(7, Types.VARCHAR); // first_name
        //          stmt.registerOutParameter(8, Types.VARCHAR); // middle_name
        //          stmt.registerOutParameter(9, Types.VARCHAR); // last_name
        //          stmt.registerOutParameter(10, Types.VARCHAR); // street1
        //          stmt.registerOutParameter(11, Types.VARCHAR); // street2
        //          stmt.registerOutParameter(12, Types.VARCHAR); // city
        //          stmt.registerOutParameter(13, Types.VARCHAR); // state
        //          stmt.registerOutParameter(14, Types.VARCHAR); // zip
        //          stmt.registerOutParameter(15, Types.VARCHAR); // phone
        //          stmt.registerOutParameter(16, Types.TIMESTAMP); // since
        //          stmt.registerOutParameter(17, Types.VARCHAR); // credit
        //          stmt.registerOutParameter(18, Types.FLOAT); // credit_lim
        //          stmt.registerOutParameter(19, Types.FLOAT); // discount
        //          stmt.registerOutParameter(20, Types.FLOAT); // balance
        //          stmt.registerOutParameter(21, Types.VARCHAR); // data
        //
        //          // Execute the block
        //          stmt.execute();
        //
        //          // Retrieve the output values
        //          c.c_first = stmt.getString(7);
        //          c.c_middle = stmt.getString(8);
        //          c.c_last = stmt.getString(9);
        //          c.c_street_1 = stmt.getString(10);
        //          c.c_street_2 = stmt.getString(11);
        //          c.c_city = stmt.getString(12);
        //          c.c_state = stmt.getString(13);
        //          c.c_zip = stmt.getString(14);
        //          c.c_phone = stmt.getString(15);
        //          c.c_since = stmt.getTimestamp(16);
        //          c.c_credit = stmt.getString(17);
        //          c.c_credit_lim = stmt.getFloat(18);
        //          c.c_discount = stmt.getFloat(19);
        //          c.c_balance = stmt.getFloat(20);
        //          c.c_data = stmt.getString(21);
        //        }
        //        try (PreparedStatement stmt =
        //            this.getPreparedStatement(conn, payUpdateCustReturningSQLOracle)) {
        //          // Check if it's actually an OraclePreparedStatement
        //          Class<?> oraclePstmtClass =
        // Class.forName("oracle.jdbc.OraclePreparedStatement");
        //          if (oraclePstmtClass.isInstance(stmt)) {
        //            // Set input parameters
        //            stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
        //            stmt.setFloat(2, paymentAmount);
        //            stmt.setString(3, dataPrefix);
        //            stmt.setInt(4, customerWarehouseID);
        //            stmt.setInt(5, customerDistrictID);
        //            stmt.setInt(6, c.c_id);
        //
        //            // Register output parameters using reflection
        //            Method registerReturnParameter =
        //                oraclePstmtClass.getMethod("registerReturnParameter", int.class,
        // int.class);
        //            for (int i = 7; i <= 15; i++) {
        //              registerReturnParameter.invoke(stmt, i, Types.VARCHAR);
        //            }
        //            registerReturnParameter.invoke(stmt, 16, Types.TIMESTAMP);
        //            registerReturnParameter.invoke(stmt, 17, Types.VARCHAR);
        //            registerReturnParameter.invoke(stmt, 18, Types.FLOAT);
        //            registerReturnParameter.invoke(stmt, 19, Types.FLOAT);
        //            registerReturnParameter.invoke(stmt, 20, Types.FLOAT);
        //            registerReturnParameter.invoke(stmt, 21, Types.VARCHAR);
        //
        //            // Execute the update
        //            stmt.executeUpdate();
        //
        //            // Dynamically access OraclePreparedStatement methods
        //            Object oracleStmt = oraclePstmtClass.cast(stmt); // Cast to the oracle
        // specific class.
        //
        //            Method getStringMethod = oraclePstmtClass.getMethod("getString", int.class);
        //            Method getTimestampMethod = oraclePstmtClass.getMethod("getTimestamp",
        // int.class);
        //            Method getFloatMethod = oraclePstmtClass.getMethod("getFloat", int.class);
        //
        //            c.c_first = (String) getStringMethod.invoke(oracleStmt, 7);
        //            c.c_middle = (String) getStringMethod.invoke(oracleStmt, 8);
        //            c.c_last = (String) getStringMethod.invoke(oracleStmt, 9);
        //            c.c_street_1 = (String) getStringMethod.invoke(oracleStmt, 10);
        //            c.c_street_2 = (String) getStringMethod.invoke(oracleStmt, 11);
        //            c.c_city = (String) getStringMethod.invoke(oracleStmt, 12);
        //            c.c_state = (String) getStringMethod.invoke(oracleStmt, 13);
        //            c.c_zip = (String) getStringMethod.invoke(oracleStmt, 14);
        //            c.c_phone = (String) getStringMethod.invoke(oracleStmt, 15);
        //            c.c_since = (Timestamp) getTimestampMethod.invoke(oracleStmt, 16);
        //            c.c_credit = (String) getStringMethod.invoke(oracleStmt, 17);
        //            c.c_credit_lim = (Float) getFloatMethod.invoke(oracleStmt, 18);
        //            c.c_discount = (Float) getFloatMethod.invoke(oracleStmt, 19);
        //            c.c_balance = (Float) getFloatMethod.invoke(oracleStmt, 20);
        //            c.c_data = (String) getStringMethod.invoke(oracleStmt, 21);
        //          } else {
        //            // FIXME: do something more elegant here.
        //            throw new RuntimeException("Invalid Oracle Setup");
        //          }
        //        } catch (ClassNotFoundException
        //            | NoSuchMethodException
        //            | IllegalAccessException
        //            | InvocationTargetException e) {
        //          // FIXME: do something more elegant here.
        //          throw new RuntimeException("Invalid Oracle Setup", e);
        //        }
      }
    } else {
      if (c.c_credit.equals("BC")) {
        // bad credit
        c.c_data =
            getCData(
                conn, w_id, districtID, customerDistrictID, customerWarehouseID, paymentAmount, c);

        updateBalanceCData(conn, customerDistrictID, customerWarehouseID, c);

      } else {
        // GoodCredit

        updateBalance(conn, customerDistrictID, customerWarehouseID, c);
      }
    }
    return c;
  }

  private Customer getCustomer(
      Connection conn,
      Random gen,
      int customerDistrictID,
      int customerWarehouseID,
      float paymentAmount)
      throws SQLException {
    int y = TPCCUtil.randomNumber(1, 100, gen);

    Customer c = new Customer();

    // Check if database supports RETURNING clause
    boolean supportsReturning = TPCCUtil.checkIfDatabaseSupportsReturning(conn);

    if (y <= 60) {
      // 60% lookups by last name
      c =
          getCustomerByName(
              customerWarehouseID,
              customerDistrictID,
              TPCCUtil.getNonUniformRandomLastNameForRun(gen),
              conn);
    } else {
      // 40% lookups by customer ID
      if (supportsReturning) {
        // In the RETURNING case, we will lookup the customer by ID later, so
        // we don't need to query the row directly here.
        c.c_id = TPCCUtil.getCustomerID(gen);
      } else {
        c =
            getCustomerById(
                customerWarehouseID, customerDistrictID, TPCCUtil.getCustomerID(gen), conn);
      }
    }

    // Only update the fields here in the case where we don't support
    // RETURNING. In cases where RETURNING is supported, the fields are updated
    // directly in the SQL statement.
    if (!supportsReturning) {
      c.c_balance -= paymentAmount;
      c.c_ytd_payment += paymentAmount;
      c.c_payment_cnt += 1;
    }

    return c;
  }

  private District updateAndGetDistrict(
      Connection conn, int w_id, int districtID, float paymentAmount) throws SQLException {
    String dbType = conn.getMetaData().getDatabaseProductName().toLowerCase();
    District d = new District();

    if (dbType.contains("postgresql") || dbType.contains("postgres")) {
      try (PreparedStatement payUpdateDist =
          this.getPreparedStatement(conn, payUpdateDistSQLPostgres)) {
        payUpdateDist.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
        payUpdateDist.setInt(2, w_id);
        payUpdateDist.setInt(3, districtID);

        try (ResultSet rs = payUpdateDist.executeQuery()) {
          if (!rs.next()) {
            throw new RuntimeException("D_ID=" + districtID + " D_W_ID=" + w_id + " not found!");
          }

          d.d_street_1 = rs.getString("D_STREET_1");
          d.d_street_2 = rs.getString("D_STREET_2");
          d.d_city = rs.getString("D_CITY");
          d.d_state = rs.getString("D_STATE");
          d.d_zip = rs.getString("D_ZIP");
          d.d_name = rs.getString("D_NAME");
        }
      }
    } else if (dbType.contains("oracle")) {
      try (PreparedStatement stmt = this.getPreparedStatement(conn, payUpdateDistSQLOracle)) {
        // Check if it's actually an OraclePreparedStatement
        Class<?> oraclePstmtClass = Class.forName("oracle.jdbc.OraclePreparedStatement");
        if (oraclePstmtClass.isInstance(stmt)) {
          // Set input parameters
          stmt.setBigDecimal(1, BigDecimal.valueOf(paymentAmount));
          stmt.setInt(2, w_id);
          stmt.setInt(3, districtID);

          // Register output parameters using reflection
          Method registerReturnParameter =
              oraclePstmtClass.getMethod("registerReturnParameter", int.class, int.class);
          for (int i = 4; i <= 9; i++) {
            registerReturnParameter.invoke(stmt, i, Types.VARCHAR);
          }

          // Execute and get results
          stmt.executeUpdate();

          // Get result set via reflection
          Method getReturnResultSet = oraclePstmtClass.getMethod("getReturnResultSet");
          ResultSet rs = (ResultSet) getReturnResultSet.invoke(stmt);

          if (rs.next()) {
            d.d_street_1 = rs.getString(1);
            d.d_street_2 = rs.getString(2);
            d.d_city = rs.getString(3);
            d.d_state = rs.getString(4);
            d.d_zip = rs.getString(5);
            d.d_name = rs.getString(6);
          }
        }
      } catch (ClassNotFoundException
          | NoSuchMethodException
          | IllegalAccessException
          | InvocationTargetException e) {
        // FIXME: change this to be a bit more elegant
        throw new RuntimeException("Missing proper support for Oracle");
      }
    }
    return d;
  }

  private String getCData(
      Connection conn,
      int w_id,
      int districtID,
      int customerDistrictID,
      int customerWarehouseID,
      float paymentAmount,
      Customer c)
      throws SQLException {

    try (PreparedStatement payGetCustCdata = this.getPreparedStatement(conn, payGetCustCdataSQL)) {
      String c_data;
      payGetCustCdata.setInt(1, customerWarehouseID);
      payGetCustCdata.setInt(2, customerDistrictID);
      payGetCustCdata.setInt(3, c.c_id);
      try (ResultSet rs = payGetCustCdata.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException(
              "C_ID="
                  + c.c_id
                  + " C_W_ID="
                  + customerWarehouseID
                  + " C_D_ID="
                  + customerDistrictID
                  + " not found!");
        }
        c_data = rs.getString("C_DATA");
      }

      c_data =
          c.c_id
              + " "
              + customerDistrictID
              + " "
              + customerWarehouseID
              + " "
              + districtID
              + " "
              + w_id
              + " "
              + paymentAmount
              + " | "
              + c_data;
      if (c_data.length() > 500) {
        c_data = c_data.substring(0, 500);
      }

      return c_data;
    }
  }

  private void updateBalanceCData(
      Connection conn, int customerDistrictID, int customerWarehouseID, Customer c)
      throws SQLException {
    try (PreparedStatement payUpdateCustBalCdata =
        this.getPreparedStatement(conn, payUpdateCustBalCdataSQL)) {
      payUpdateCustBalCdata.setFloat(1, c.c_balance);
      payUpdateCustBalCdata.setFloat(2, c.c_ytd_payment);
      payUpdateCustBalCdata.setInt(3, c.c_payment_cnt);
      payUpdateCustBalCdata.setString(4, c.c_data);
      payUpdateCustBalCdata.setInt(5, customerWarehouseID);
      payUpdateCustBalCdata.setInt(6, customerDistrictID);
      payUpdateCustBalCdata.setInt(7, c.c_id);

      int result = payUpdateCustBalCdata.executeUpdate();

      if (result == 0) {
        throw new RuntimeException(
            "Error in PYMNT Txn updating Customer C_ID="
                + c.c_id
                + " C_W_ID="
                + customerWarehouseID
                + " C_D_ID="
                + customerDistrictID);
      }
    }
  }

  private void updateBalance(
      Connection conn, int customerDistrictID, int customerWarehouseID, Customer c)
      throws SQLException {

    try (PreparedStatement payUpdateCustBal =
        this.getPreparedStatement(conn, payUpdateCustBalSQL)) {
      payUpdateCustBal.setFloat(1, c.c_balance);
      payUpdateCustBal.setFloat(2, c.c_ytd_payment);
      payUpdateCustBal.setInt(3, c.c_payment_cnt);
      payUpdateCustBal.setInt(4, customerWarehouseID);
      payUpdateCustBal.setInt(5, customerDistrictID);
      payUpdateCustBal.setInt(6, c.c_id);

      int result = payUpdateCustBal.executeUpdate();

      if (result == 0) {
        throw new RuntimeException(
            "C_ID="
                + c.c_id
                + " C_W_ID="
                + customerWarehouseID
                + " C_D_ID="
                + customerDistrictID
                + " not found!");
      }
    }
  }

  private void insertHistory(
      Connection conn,
      int w_id,
      int districtID,
      int customerDistrictID,
      int customerWarehouseID,
      float paymentAmount,
      String w_name,
      String d_name,
      Customer c)
      throws SQLException {
    if (w_name.length() > 10) {
      w_name = w_name.substring(0, 10);
    }
    if (d_name.length() > 10) {
      d_name = d_name.substring(0, 10);
    }
    String h_data = w_name + "    " + d_name;

    try (PreparedStatement payInsertHist = this.getPreparedStatement(conn, payInsertHistSQL)) {
      payInsertHist.setInt(1, customerDistrictID);
      payInsertHist.setInt(2, customerWarehouseID);
      payInsertHist.setInt(3, c.c_id);
      payInsertHist.setInt(4, districtID);
      payInsertHist.setInt(5, w_id);
      payInsertHist.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
      payInsertHist.setDouble(7, paymentAmount);
      payInsertHist.setString(8, h_data);
      payInsertHist.executeUpdate();
    }
  }

  // attention duplicated code across trans... ok for now to maintain separate
  // prepared statements
  public Customer getCustomerById(int c_w_id, int c_d_id, int c_id, Connection conn)
      throws SQLException {

    try (PreparedStatement payGetCust = this.getPreparedStatement(conn, payGetCustSQL)) {

      payGetCust.setInt(1, c_w_id);
      payGetCust.setInt(2, c_d_id);
      payGetCust.setInt(3, c_id);

      try (ResultSet rs = payGetCust.executeQuery()) {
        if (!rs.next()) {
          throw new RuntimeException(
              "C_ID=" + c_id + " C_D_ID=" + c_d_id + " C_W_ID=" + c_w_id + " not found!");
        }

        Customer c = TPCCUtil.newCustomerFromResults(rs);
        c.c_id = c_id;
        c.c_last = rs.getString("C_LAST");
        return c;
      }
    }
  }

  // attention this code is repeated in other transacitons... ok for now to
  // allow for separate statements.
  public Customer getCustomerByName(
      int c_w_id, int c_d_id, String customerLastName, Connection conn) throws SQLException {
    ArrayList<Customer> customers = new ArrayList<>();

    try (PreparedStatement customerByName = this.getPreparedStatement(conn, customerByNameSQL)) {

      customerByName.setInt(1, c_w_id);
      customerByName.setInt(2, c_d_id);
      customerByName.setString(3, customerLastName);
      try (ResultSet rs = customerByName.executeQuery()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("C_LAST={} C_D_ID={} C_W_ID={}", customerLastName, c_d_id, c_w_id);
        }

        while (rs.next()) {
          Customer c = TPCCUtil.newCustomerFromResults(rs);
          c.c_id = rs.getInt("C_ID");
          c.c_last = customerLastName;
          customers.add(c);
        }
      }
    }

    if (customers.size() == 0) {
      throw new RuntimeException(
          "C_LAST=" + customerLastName + " C_D_ID=" + c_d_id + " C_W_ID=" + c_w_id + " not found!");
    }

    // TPC-C 2.5.2.2: Position n / 2 rounded up to the next integer, but
    // that
    // counts starting from 1.
    int index = customers.size() / 2;
    if (customers.size() % 2 == 0) {
      index -= 1;
    }
    return customers.get(index);
  }
}
