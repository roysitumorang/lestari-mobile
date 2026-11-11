package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.BRANCHES;
import static id.lestari.mobile.defs.Common.CATEGORIES;
import static id.lestari.mobile.defs.Common.PRODUCTS;
import static id.lestari.mobile.defs.Common.list;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import java.util.ArrayList;
import java.util.List;

/** Branches group: multi-outlet management from the head office (js/modules/branches.js). */
final class BranchDefs {
    private BranchDefs() {}

    static void register(Registry r) {
        r.add(new Page(Page.Kind.CRUD, "branch-billing", "Branch Billing").singular("Branch subscription").endpoint("/billing/branches")
                .desc("Renew the subscription of several branches in one payment.")
                .ops("list").idKey("branch_id").titleKey("branch_name")
                .columns(Field.col("branch_name", "Branch"), Field.col("plan", "Plan", "badge"), Field.col("expires_at", "Active until", "date"),
                        Field.col("status", "Status", "badge"), Field.col("renewal_price", "Renewal price", "money"))
                .toolbar(Action.call("pay", "Pay for branches", "POST", "/billing/branch-renewals").success("Invoice created").after(Action.OPEN_PAYMENT)
                        .form(Field.multiLookup("branch_ids", "Branches", BRANCHES).req().w(12),
                                Field.select("period", "Period", Opt.pairs("monthly", "1 month", "quarterly", "3 months", "yearly", "12 months")).req().def("monthly"),
                                Field.select("payment_method", "Pay with", Opt.pairs("coin", "Premium coin balance", "virtual_account", "Virtual account", "qris", "QRIS",
                                        "e_wallet", "E-wallet", "credit_card", "Credit card", "manual_transfer", "Manual bank transfer")).req().def("coin"),
                                Field.text("promo_code", "Promo code"))));

        r.add(new Page(Page.Kind.REPORT, "branch-dashboard", "Branch Dashboard").endpoint("/reports/branches").preset("this_month").notPaginated()
                .desc("Sales performance of every branch side by side.")
                .summary(Field.col("transaction_count", "Transactions", "number"), Field.col("revenue", "Revenue", "money"), Field.col("profit", "Profit", "money"))
                .chart(new Page.Chart("Revenue per branch", "bar", "branch_name", "data", "money").series("revenue", "Revenue").series("profit", "Profit"))
                .columns(Field.col("branch_name", "Branch"), Field.col("transaction_count", "Transactions", "number"), Field.col("revenue", "Revenue", "money"),
                        Field.col("profit", "Profit", "money"), Field.col("average_basket", "Avg. basket", "money")));

        r.add(new Page(Page.Kind.CRUD, "branch-products", "Branch Products").singular("Branch product").endpoint("/branch-products").plugin("business")
                .desc("Per-branch price and stock of head-office products.")
                .ops("list", "update", "export").search("Search product")
                .filters(Field.lookup("branch_id", "Branch", BRANCHES), Field.lookup("category_id", "Category", CATEGORIES))
                .columns(Field.col("branch_name", "Branch"), Field.col("product_name", "Product"), Field.col("sku", "SKU"), Field.col("stock", "Stock", "number"),
                        Field.col("selling_price", "Price", "money"), Field.col("is_available", "Available", "boolean"))
                .titleKey("product_name")
                .fields(Field.money("selling_price", "Selling price in this branch").req(), Field.money("purchase_price", "Cost in this branch"),
                        Field.sw("is_available", "Available in this branch").def(true))
                .toolbar(Action.call("copy_prices", "Copy head-office prices", "POST", "/branch-products/sync-prices").success("Prices copied")
                        .form(Field.multiLookup("branch_ids", "Branches", BRANCHES).req().w(12),
                                Field.multiLookup("product_ids", "Products (empty = all)", PRODUCTS).w(12))));

        r.add(new Page(Page.Kind.CRUD, "stock-transfers", "Stock Transfers").singular("Stock transfer").endpoint("/stock-transfers")
                .desc("Send stock between head office and branches.")
                .ops("list", "create", "read", "delete").titleKey("transfer_number").sort("transfer_date", "desc").search("Search transfer number")
                .filters(Field.select("direction", "Direction", Opt.pairs("out", "Outgoing", "in", "Incoming")))
                .columns(Field.col("transfer_date", "Date", "date"), Field.col("transfer_number", "Number"), Field.col("from_branch.name", "From"),
                        Field.col("to_branch.name", "To"), Field.col("total_quantity", "Qty", "number"), Field.col("status", "Status", "badge"))
                .fields(Field.lookup("to_branch_id", "Send to", BRANCHES).req(), Field.date("transfer_date", "Date").req(),
                        Field.sw("requires_confirmation", "Receiver must confirm").def(false).help("Stock arrives only after the receiving branch accepts."),
                        Field.items("items", "Items", Field.lookup("product_id", "Product", PRODUCTS), Field.number("quantity", "Qty"), Field.text("note", "Note")),
                        Field.textarea("note", "Note"))
                .detailFields(Field.col("transfer_number", "Number"), Field.date("transfer_date", "Date"), Field.col("from_branch.name", "From"),
                        Field.col("to_branch.name", "To"), Field.col("status", "Status"),
                        Field.items("items", "Items", Field.col("product_name", "Product"), Field.col("quantity", "Qty", "number"),
                                Field.col("received_quantity", "Received", "number"), Field.col("note", "Note")),
                        Field.col("note", "Note"))
                .rowActions(Action.download("print", "Print transfer note", "/stock-transfers/{id}/print", "transfer.pdf")));

        List<Field> transferCols = list(Field.col("transfer_date", "Date", "date"), Field.col("transfer_number", "Number"),
                Field.col("direction", "Type", "badge"), Field.col("from_branch_name", "From"), Field.col("to_branch_name", "To"),
                Field.col("item_count", "Items", "number"), Field.col("total_quantity", "Qty", "number"), Field.col("status", "Status", "badge"));
        List<Field> transferFilters = list(Field.select("direction", "Type", Opt.pairs("out", "Outgoing", "in", "Incoming")),
                Field.select("role", "Position", Opt.pairs("head_office", "Head office", "branch", "Branch")));

        r.add(new Page(Page.Kind.REPORT, "report-transfers", "Transfer Report").endpoint("/reports/stock-transfers").search("Search transfer number")
                .filters(transferFilters).columns(transferCols)
                .detail(new Page.Detail("/stock-transfers/{id}", "Transfer").fields(Field.col("transfer_number", "Number"), Field.col("status", "Status"),
                        Field.items("items", "Items", Field.col("product_name", "Product"), Field.col("quantity", "Qty", "number"),
                                Field.col("received_quantity", "Received", "number")))));

        List<Field> confirmFilters = new ArrayList<>(transferFilters);
        confirmFilters.add(Field.select("status", "Status", Opt.pairs("pending", "Pending", "accepted", "Accepted", "rejected", "Rejected")));
        r.add(new Page(Page.Kind.REPORT, "report-transfer-confirmations", "Transfers Awaiting Confirmation").plugin("business")
                .endpoint("/reports/stock-transfers/confirmations").search("Search transfer number")
                .filters(confirmFilters).columns(transferCols)
                .rowActions(
                        Action.call("accept", "Accept", "POST", "/stock-transfers/{id}/accept").success("Transfer accepted")
                                .when(row -> "pending".equals(Json.str(row, "status")) && "in".equals(Json.str(row, "direction")))
                                .form(Field.items("items", "Received quantities (leave empty to accept all)", Field.lookup("product_id", "Product", PRODUCTS),
                                        Field.number("received_quantity", "Received")), Field.textarea("note", "Note")),
                        Action.call("reject", "Reject", "POST", "/stock-transfers/{id}/reject").success("Transfer rejected")
                                .when(row -> "pending".equals(Json.str(row, "status")) && "in".equals(Json.str(row, "direction")))
                                .form(Field.textarea("reason", "Reason").req())));

        r.add(new Page(Page.Kind.CRUD, "branches", "Branches").singular("Branch").endpoint("/branches")
                .desc("Outlets managed from this head-office account.")
                .ops("list", "create", "read", "update", "delete")
                .columns(Field.col("name", "Name"), Field.col("code", "Code"), Field.col("phone", "Phone"), Field.col("address", "Address"),
                        Field.col("is_head_office", "Head office", "boolean"), Field.col("subscription_expires_at", "Active until", "date"))
                .fields(Field.text("name", "Branch name").req(), Field.text("code", "Branch code"), Field.text("manager_name", "Manager"),
                        Field.tel("phone", "Phone"), Field.email("email", "Login email for the branch").req(),
                        Field.password("password", "Password").help("Leave blank when editing to keep the current password.").noList(),
                        Field.textarea("address", "Address"))
                .rowActions(
                        Action.call("copy_products", "Copy products from head office", "POST", "/branches/{id}/copy-products").success("Copy started")
                                .when(row -> !row.optBoolean("is_head_office"))
                                .form(Field.multiLookup("category_ids", "Categories (empty = all)", CATEGORIES).w(12),
                                        Field.sw("include_stock", "Copy stock quantities too")),
                        Action.custom("switch", "Open this branch", (host, row, ctx) -> host.switchBranch(Json.str(row, "id")))));
    }
}
