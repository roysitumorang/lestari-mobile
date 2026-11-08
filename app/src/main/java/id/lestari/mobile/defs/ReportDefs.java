package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.CASH_DRAWERS;
import static id.lestari.mobile.defs.Common.CATEGORIES;
import static id.lestari.mobile.defs.Common.CUSTOMERS;
import static id.lestari.mobile.defs.Common.PAYMENT_TYPE;
import static id.lestari.mobile.defs.Common.PROMOTIONS;
import static id.lestari.mobile.defs.Common.RECEIPT_COLUMNS;
import static id.lestari.mobile.defs.Common.SALES_PAYMENT_METHODS;
import static id.lestari.mobile.defs.Common.STAFF;
import static id.lestari.mobile.defs.Common.SUPPLIERS;
import static id.lestari.mobile.defs.Common.list;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import java.util.List;

/** Reports group: GET /reports/<name>?start_date&end_date&… and …/export for downloads (js/modules/reports.js). */
final class ReportDefs {
    private ReportDefs() {}

    private static Page report(Registry r, String id, String title, String endpoint) {
        return r.add(new Page(Page.Kind.REPORT, id, title).endpoint(endpoint));
    }

    private static Page.Detail receipts(String endpoint, String title) {
        return new Page.Detail(endpoint, title).table("sales", RECEIPT_COLUMNS);
    }

    static void register(Registry r) {
        report(r, "report-profit-loss", "Profit & Loss", "/reports/profit-loss").preset("this_month").notPaginated()
                .filters(Field.sw("include_unpaid", "Count credit sales before they are paid").w(12))
                .summary(Field.col("gross_sales", "Sales", "money"), Field.col("cost_of_goods_sold", "Cost of goods sold", "money"),
                        Field.col("other_income", "Other income", "money"), Field.col("net_profit", "Net profit", "money"))
                .chart(new Page.Chart("Daily profit", "bar", "date", "data", "money").series("gross_profit", "Gross profit").series("net_profit", "Net profit"))
                .columns(Field.col("date", "Date", "date"), Field.col("gross_sales", "Sales", "money"), Field.col("discounts", "Discounts", "money"),
                        Field.col("cost_of_goods_sold", "COGS", "money"), Field.col("gross_profit", "Gross profit", "money"),
                        Field.col("other_income", "Other income", "money"), Field.col("mdr_fees", "MDR fees", "money"),
                        Field.col("commissions", "Commissions", "money"), Field.col("other_expenses", "Expenses", "money"),
                        Field.col("net_profit", "Net profit", "money"));

        report(r, "report-cash-flow", "Cash Flow", "/reports/cash-flow").preset("this_month")
                .filters(Field.select("type", "Type", Opt.pairs("income", "Income", "expense", "Expense")))
                .summary(Field.col("total_income", "Income", "money"), Field.col("total_expense", "Expense", "money"), Field.col("net_cash_flow", "Net", "money"))
                .columns(Field.col("date", "Date", "datetime"), Field.col("type", "Type", "badge"), Field.col("source", "Source"),
                        Field.col("category", "Category"), Field.col("description", "Description"), Field.col("amount", "Amount", "money"));

        report(r, "report-daily-sales", "Daily Sales", "/reports/daily-sales").preset("this_month").notPaginated()
                .desc("One row per day. Open a day to see its receipts.")
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("net_sales", "Net sales", "money"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("date", "Date", "date"), Field.col("transaction_count", "Transactions", "number"), Field.col("gross_sales", "Gross", "money"),
                        Field.col("discounts", "Discounts", "money"), Field.col("taxes", "Tax", "money"), Field.col("net_sales", "Net", "money"),
                        Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/daily-sales/{date}", "Receipts of the day"))
                .toolbar(Action.call("resync", "Rebuild report", "POST", "/reports/daily-sales/rebuild").useFilters()
                        .confirm("Rebuild daily totals for the selected period from the raw transactions?").success("Rebuild started"));

        report(r, "report-sales-transactions", "Sales Transactions", "/reports/sales-transactions").search("Search receipt number")
                .filters(Field.select("payment_method", "Payment method", SALES_PAYMENT_METHODS), Field.select("payment_type", "Payment type", PAYMENT_TYPE),
                        Field.lookup("cashier_id", "Cashier", STAFF))
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("grand_total", "Total billed", "money"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("cashier_name", "Cashier"),
                        Field.col("payment_method", "Method"), Field.col("payment_type", "Type"), Field.col("profit", "Profit", "money"),
                        Field.col("grand_total", "Total", "money"), Field.col("paid_amount", "Paid", "money"), Field.col("service_charge", "Service", "money"),
                        Field.col("rounding", "Rounding", "money"), Field.col("label", "Label"), Field.col("customer_type", "Customer type"))
                .detail(new Page.Detail("/sales/{id}", "Sale").fields(TransactionDefs.SALE_DETAIL));

        report(r, "report-sales-by-product", "Sales by Product", "/reports/sales-by-product").search("Search product")
                .filters(Field.lookup("category_id", "Category", CATEGORIES))
                .summary(Field.col("quantity_sold", "Units sold", "number"), Field.col("net_sales", "Net sales", "money"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("product_name", "Product"), Field.col("sku", "SKU"), Field.col("category_name", "Category"),
                        Field.col("quantity_sold", "Qty", "number"), Field.col("gross_sales", "Gross", "money"), Field.col("discounts", "Discounts", "money"),
                        Field.col("net_sales", "Net", "money"), Field.col("cost_of_goods_sold", "COGS", "money"), Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/sales-by-product/{product_id}", "Sales of this product"));

        report(r, "report-sales-by-category", "Sales by Category", "/reports/sales-by-category").search("Search category").notPaginated()
                .summary(Field.col("quantity_sold", "Units sold", "number"), Field.col("net_sales", "Net sales", "money"), Field.col("profit", "Profit", "money"))
                .chart(new Page.Chart("Net sales by category", "bar", "category_name", "data", "money").series("net_sales", "Net sales"))
                .columns(Field.col("category_name", "Category"), Field.col("product_count", "Products", "number"), Field.col("quantity_sold", "Qty", "number"),
                        Field.col("gross_sales", "Gross", "money"), Field.col("net_sales", "Net", "money"), Field.col("profit", "Profit", "money"));

        report(r, "report-daily-purchases", "Daily Purchases", "/reports/daily-purchases").preset("this_month").search("Search purchase number").notPaginated()
                .summary(Field.col("purchase_count", "Purchases", "number"), Field.col("grand_total", "Total", "money"))
                .columns(Field.col("date", "Date", "date"), Field.col("purchase_count", "Purchases", "number"), Field.col("item_quantity", "Items", "number"),
                        Field.col("grand_total", "Total", "money"), Field.col("paid_amount", "Paid", "money"));

        report(r, "report-purchase-transactions", "Purchase Transactions", "/reports/purchase-transactions").search("Search purchase number")
                .filters(Field.select("payment_type", "Payment type", PAYMENT_TYPE), Field.lookup("staff_id", "Recorded by", STAFF),
                        Field.lookup("supplier_id", "Supplier", SUPPLIERS))
                .summary(Field.col("purchase_count", "Purchases", "number"), Field.col("grand_total", "Total", "money"))
                .columns(Field.col("purchase_date", "Date", "date"), Field.col("receipt_number", "Number"), Field.col("supplier_name", "Supplier"),
                        Field.col("staff_name", "Recorded by"), Field.col("payment_type", "Payment"), Field.col("grand_total", "Total", "money"),
                        Field.col("paid_amount", "Paid", "money"))
                .detail(new Page.Detail("/purchases/{id}", "Purchase").fields(Field.col("receipt_number", "Number"), Field.date("purchase_date", "Date"),
                        Field.col("supplier.name", "Supplier"),
                        Field.items("items", "Items", Field.col("product_name", "Item"), Field.col("quantity", "Qty", "number"),
                                Field.col("unit_cost", "Cost", "money"), Field.col("subtotal", "Subtotal", "money")),
                        Field.money("grand_total", "Total")));

        // ---- points ----
        report(r, "report-customer-points", "Points by Customer", "/reports/customer-points").search("Search customer")
                .filters(Field.lookup("customer_id", "Customer", CUSTOMERS))
                .summary(Field.col("points_redeemed", "Points redeemed", "number"), Field.col("points_earned", "Points earned", "number"),
                        Field.col("transaction_count", "Transactions", "number"))
                .columns(Field.col("customer_name", "Customer"), Field.col("customer_code", "Code"), Field.col("points_earned", "Earned", "number"),
                        Field.col("points_redeemed", "Redeemed", "number"), Field.col("transaction_count", "Transactions", "number"),
                        Field.col("points_balance", "Balance", "number"))
                .detail(receipts("/reports/customer-points/{customer_id}", "Point transactions"));
        report(r, "report-point-redemptions", "Point Redemptions", "/reports/point-redemptions").search("Search product")
                .summary(Field.col("points_redeemed", "Points redeemed", "number"), Field.col("quantity", "Quantity", "number"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("product_name", "Product"), Field.col("quantity", "Qty", "number"), Field.col("transaction_count", "Transactions", "number"),
                        Field.col("points_redeemed", "Points", "number"), Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/point-redemptions/{product_id}", "Redemptions"));
        report(r, "report-point-earnings", "Point-Earning Sales", "/reports/point-earnings").search("Search product")
                .summary(Field.col("points_earned", "Points earned", "number"), Field.col("quantity", "Quantity", "number"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("product_name", "Product"), Field.col("quantity", "Qty", "number"), Field.col("transaction_count", "Transactions", "number"),
                        Field.col("points_earned", "Points", "number"), Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/point-earnings/{product_id}", "Sales"));

        report(r, "report-capital", "Inventory Capital", "/reports/capital").noDates().search("Search product")
                .desc("Value of stock on hand at cost.")
                .filters(Field.lookup("category_id", "Category", CATEGORIES))
                .summary(Field.col("total_capital", "Total capital in stock", "money"), Field.col("total_stock", "Units in stock", "number"))
                .columns(Field.col("product_name", "Product"), Field.col("sku", "SKU"), Field.col("category_name", "Category"),
                        Field.col("stock", "Stock", "number"), Field.col("unit_cost", "Unit cost", "money"), Field.col("capital_value", "Capital", "money"));

        report(r, "report-shifts", "Shifts", "/reports/shifts")
                .filters(Field.lookup("cash_drawer_id", "Cash drawer", CASH_DRAWERS), Field.lookup("cashier_id", "Cashier", STAFF))
                .columns(Field.col("opened_at", "Opened", "datetime"), Field.col("closed_at", "Closed", "datetime"), Field.col("cash_drawer_name", "Drawer"),
                        Field.col("cashier_name", "Cashier"), Field.col("opening_cash", "Opening", "money"), Field.col("expected_cash", "Expected", "money"),
                        Field.col("actual_cash", "Counted", "money"), Field.col("difference", "Difference", "money"))
                .detail(new Page.Detail("/reports/shifts/{id}", "Shift detail").fields(
                        Field.col("cash_drawer_name", "Cash drawer"), Field.col("cashier_name", "Cashier"), Field.datetime("opened_at", "Opened"),
                        Field.datetime("closed_at", "Closed"), Field.money("opening_cash", "Opening cash"), Field.money("cash_sales", "Cash sales"),
                        Field.money("cash_in", "Cash in"), Field.money("cash_out", "Cash out"), Field.money("expected_cash", "Expected cash"),
                        Field.money("actual_cash", "Counted cash"), Field.money("difference", "Difference"),
                        Field.items("payments", "Sales by payment method", Field.col("payment_method", "Method"),
                                Field.col("transaction_count", "Transactions", "number"), Field.col("amount", "Amount", "money"))))
                .rowActions(Action.download("shift_pdf", "Download shift report", "/reports/shifts/{id}/export", "shift.pdf"));

        List<Field> feeCols = list(Field.col("created_at", "Date", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("cashier_name", "Cashier"),
                Field.col("fee_name", "Fee"), Field.col("amount", "Amount", "money"));
        report(r, "report-fees-sales", "Fees on Sales", "/reports/fees/sales").search("Search receipt")
                .filters(Field.lookup("cashier_id", "Cashier", STAFF), Field.select("payment_method", "Payment method", SALES_PAYMENT_METHODS))
                .summary(Field.col("total_amount", "Total fees", "money"), Field.col("transaction_count", "Transactions", "number")).columns(feeCols);
        report(r, "report-fees-purchases", "Fees on Purchases", "/reports/fees/purchases").search("Search purchase")
                .filters(Field.lookup("staff_id", "Recorded by", STAFF))
                .summary(Field.col("total_amount", "Total fees", "money"), Field.col("transaction_count", "Transactions", "number")).columns(feeCols);
        report(r, "report-mdr", "MDR Fees", "/reports/fees/mdr").search("Search receipt")
                .desc("Merchant discount rate charged by card / e-wallet providers.")
                .filters(Field.lookup("cashier_id", "Cashier", STAFF), Field.select("payment_method", "Payment method", SALES_PAYMENT_METHODS))
                .summary(Field.col("total_amount", "Total MDR", "money"), Field.col("transaction_count", "Transactions", "number"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("cashier_name", "Cashier"),
                        Field.col("payment_method", "Method"), Field.col("transaction_amount", "Sale", "money"), Field.col("mdr_rate", "Rate", "percent"),
                        Field.col("amount", "MDR", "money"));

        report(r, "report-taxes", "Taxes", "/reports/taxes")
                .summary(Field.col("taxable_amount", "Taxable sales", "money"), Field.col("tax_amount", "Tax collected", "money"))
                .columns(Field.col("date", "Date", "date"), Field.col("tax_name", "Tax"), Field.col("rate", "Rate", "percent"),
                        Field.col("transaction_count", "Transactions", "number"), Field.col("taxable_amount", "Taxable", "money"), Field.col("tax_amount", "Tax", "money"));

        report(r, "report-ppob", "PPOB Sales", "/reports/ppob").search("Search reference ID")
                .filters(Field.lookup("cashier_id", "Cashier", STAFF), Field.select("status", "Status", Opt.of("success", "pending", "failed")))
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("selling_total", "Sales", "money"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("reference_id", "Reference"), Field.col("product_name", "Product"),
                        Field.col("customer_number", "Customer no."), Field.col("base_price", "Cost", "money"), Field.col("selling_price", "Price", "money"),
                        Field.col("profit", "Profit", "money"), Field.col("cashier_name", "Cashier"), Field.col("status", "Status", "badge"));

        report(r, "report-visitors", "Visitors", "/reports/visitors").preset("last_7_days").notPaginated()
                .desc("Customer visits (transactions) grouped by time.")
                .groupBy("group_by", "Group by", Opt.pairs("hour", "Hour", "weekday", "Day of week", "date", "Date", "month", "Month"))
                .summary(Field.col("visitor_count", "Visitors", "number"), Field.col("average_per_period", "Average", "number"))
                .chart(new Page.Chart("Visitors", "bar", "period", "data", "number").series("visitor_count", "Visitors"))
                .columns(Field.col("period", "Period"), Field.col("visitor_count", "Visitors", "number"), Field.col("net_sales", "Sales", "money"));

        report(r, "report-promotion-transactions", "Promotion Transactions", "/reports/promotions/transactions").search("Search promotion")
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("discount_total", "Discount given", "money"),
                        Field.col("net_sales", "Revenue", "money"), Field.col("profit", "Profit", "money"))
                .columns(Field.col("promotion_name", "Promotion"), Field.col("transaction_count", "Transactions", "number"),
                        Field.col("discount_total", "Discount", "money"), Field.col("net_sales", "Revenue", "money"), Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/promotions/transactions/{promotion_id}", "Transactions"));
        report(r, "report-promotion-products", "Promotion Product Sales", "/reports/promotions/products").search("Search product")
                .filters(Field.lookup("promotion_id", "Promotion", PROMOTIONS))
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("profit", "Profit", "money"), Field.col("net_sales", "Revenue", "money"))
                .columns(Field.col("product_name", "Product"), Field.col("promotion_name", "Promotion"), Field.col("quantity", "Qty", "number"),
                        Field.col("discount_total", "Discount", "money"), Field.col("net_sales", "Revenue", "money"), Field.col("profit", "Profit", "money"))
                .detail(receipts("/reports/promotions/products/{product_id}", "Sales"));

        report(r, "report-store-ratings", "Store Ratings", "/reports/store-ratings").preset("last_30_days")
                .filters(Field.select("rating", "Stars", Opt.pairs(5, "5 ★", 4, "4 ★", 3, "3 ★", 2, "2 ★", 1, "1 ★")))
                .summary(Field.col("average_rating", "Average rating", "number"), Field.col("review_count", "Reviews", "number"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("customer_name", "Customer"),
                        Field.col("rating", "Rating").render((v, row) -> stars((int) Json.num(v))),
                        Field.col("comment", "Comment"), Field.col("receipt_number", "Receipt"))
                .detail(new Page.Detail("/reports/store-ratings/{id}", "Review").fields(Field.col("customer_name", "Customer"),
                        Field.col("rating", "Rating", "number"), Field.col("comment", "Comment"), Field.col("tags", "Tags", "list"),
                        Field.col("receipt_number", "Receipt"), Field.datetime("created_at", "Date")));
    }

    static String stars(int n) {
        n = Math.max(0, Math.min(5, n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? '★' : '☆');
        return sb.toString();
    }
}
