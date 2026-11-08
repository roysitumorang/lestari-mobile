package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.CASH_DRAWERS;
import static id.lestari.mobile.defs.Common.CUSTOMERS;
import static id.lestari.mobile.defs.Common.PAYMENT_TYPE;
import static id.lestari.mobile.defs.Common.PRODUCTS;
import static id.lestari.mobile.defs.Common.SALES_PAYMENT_METHODS;
import static id.lestari.mobile.defs.Common.STAFF;
import static id.lestari.mobile.defs.Common.SUPPLIERS;
import static id.lestari.mobile.defs.Common.list;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import java.time.LocalDate;
import java.util.List;

/** Transactions group (sales, purchases, cash in/out) plus debts and returns (js/modules/transactions.js). */
final class TransactionDefs {
    private TransactionDefs() {}

    static final List<Field> SALE_DETAIL = list(
            Field.col("receipt_number", "Receipt number"), Field.datetime("created_at", "Date"),
            Field.col("cashier_name", "Cashier"), Field.col("customer.name", "Customer"),
            Field.col("payment_method", "Payment method"), Field.select("payment_type", "Payment type", PAYMENT_TYPE),
            Field.items("items", "Items", Field.col("product_name", "Item"), Field.col("variant_name", "Variant"), Field.col("quantity", "Qty", "number"),
                    Field.col("unit_price", "Price", "money"), Field.col("discount_amount", "Discount", "money"), Field.col("subtotal", "Subtotal", "money")),
            Field.money("subtotal", "Subtotal"), Field.money("discount_total", "Discount"),
            Field.money("tax_total", "Tax"), Field.money("service_charge", "Service charge"),
            Field.money("fee_total", "Fees"), Field.money("rounding", "Rounding"),
            Field.money("grand_total", "Total"), Field.money("paid_amount", "Paid"),
            Field.money("change_amount", "Change"), Field.money("profit", "Profit"),
            Field.col("label", "Label / table"), Field.col("note", "Note"), Field.col("status", "Status"));

    static void register(Registry r) {
        r.add(new Page(Page.Kind.CRUD, "sales", "Sales").singular("Sale").endpoint("/sales")
                .desc("Sales recorded by the cashier app. Open a sale to see its receipt.")
                .ops("list", "read", "delete", "export").readOnly().titleKey("receipt_number")
                .search("Search receipt number or customer").sort("created_at", "desc")
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"),
                        Field.select("payment_method", "Payment method", SALES_PAYMENT_METHODS),
                        Field.select("payment_type", "Payment type", PAYMENT_TYPE), Field.lookup("cashier_id", "Cashier", STAFF))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("customer.name", "Customer"),
                        Field.col("cashier_name", "Cashier"), Field.col("payment_method", "Method"), Field.select("payment_type", "Type", PAYMENT_TYPE),
                        Field.col("grand_total", "Total", "money"), Field.col("status", "Status", "badge"))
                .fields(SALE_DETAIL).detailFields(SALE_DETAIL)
                .rowActions(Action.download("receipt", "Download receipt", "/sales/{id}/receipt", "receipt.pdf"),
                        Action.download("delivery_note", "Delivery note", "/sales/{id}/delivery-note", "delivery-note.pdf")));

        r.add(new Page(Page.Kind.CRUD, "purchases", "Purchases").singular("Purchase").endpoint("/purchases")
                .desc("Stock bought from suppliers. Saving a purchase adds stock and, for credit purchases, a payable.")
                .ops("list", "create", "read", "update", "delete", "export").titleKey("receipt_number").sort("purchase_date", "desc")
                .search("Search purchase number or supplier")
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"), Field.lookup("supplier_id", "Supplier", SUPPLIERS),
                        Field.select("payment_type", "Payment type", PAYMENT_TYPE))
                .columns(Field.col("purchase_date", "Date", "date"), Field.col("receipt_number", "Number"), Field.col("supplier.name", "Supplier"),
                        Field.select("payment_type", "Payment", PAYMENT_TYPE), Field.col("grand_total", "Total", "money"),
                        Field.col("paid_amount", "Paid", "money"), Field.col("status", "Status", "badge"))
                .fields(Field.lookup("supplier_id", "Supplier", SUPPLIERS), Field.date("purchase_date", "Date").req(),
                        Field.text("invoice_number", "Supplier invoice no."),
                        Field.select("payment_type", "Payment type", PAYMENT_TYPE).req().def("cash"),
                        Field.select("payment_method", "Payment method", SALES_PAYMENT_METHODS).def("cash"),
                        Field.date("due_date", "Due date").showIf("payment_type", "credit"),
                        Field.money("paid_amount", "Amount paid now").min(0).showIf("payment_type", "credit"),
                        Field.items("items", "Items", Field.lookup("product_id", "Product", PRODUCTS), Field.number("quantity", "Qty"),
                                Field.text("unit", "Unit"), Field.money("unit_cost", "Unit cost"), Field.money("discount_amount", "Discount")),
                        Field.money("discount_total", "Invoice discount").min(0), Field.money("tax_total", "Tax").min(0),
                        Field.money("fee_total", "Other fees").min(0), Field.money("grand_total", "Total").noForm(), Field.textarea("note", "Note"))
                .rowActions(Action.call("return", "Return items", "POST", "/purchases/{id}/returns").success("Return recorded")
                        .form(Field.items("items", "Items to return", Field.lookup("product_id", "Product", PRODUCTS), Field.number("quantity", "Qty")),
                                Field.select("refund_method", "Refund as", Opt.pairs("cash", "Cash", "reduce_payable", "Reduce debt")),
                                Field.textarea("reason", "Reason"))));

        r.add(new Page(Page.Kind.CRUD, "finance", "Cash In & Out").singular("Entry").endpoint("/finance-entries")
                .desc("Income and expenses outside sales and purchases (rent, salaries, capital injections…).")
                .ops("list", "create", "read", "update", "delete", "export").titleKey("description").sort("entry_date", "desc")
                .tabs("type", "", Opt.pairs("", "All", "income", "Income", "expense", "Expense"))
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"))
                .fields(Field.date("entry_date", "Date").req(), Field.select("type", "Type", Opt.pairs("income", "Income", "expense", "Expense")).req(),
                        Field.text("category", "Category").ph("Rent, salary, capital…").req(), Field.money("amount", "Amount").req().min(0),
                        Field.select("payment_method", "Paid with", SALES_PAYMENT_METHODS), Field.lookup("cash_drawer_id", "Cash drawer", CASH_DRAWERS).noList(),
                        Field.textarea("description", "Description"), Field.image("attachment_url", "Receipt photo").w(12).noList()));

        debtPage(r, "payables", "Payables", "/payables", "supplier", "Supplier", SUPPLIERS);
        debtPage(r, "receivables", "Receivables", "/receivables", "customer", "Customer", CUSTOMERS);

        r.add(new Page(Page.Kind.CRUD, "sales-returns", "Sales Returns").singular("Sales return").endpoint("/sales-returns")
                .ops("list", "read", "export").readOnly().titleKey("return_number").sort("created_at", "desc").search("Search receipt number")
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("return_number", "Return no."), Field.col("receipt_number", "Original receipt"),
                        Field.col("customer.name", "Customer"), Field.col("item_count", "Items", "number"), Field.col("refund_amount", "Refund", "money"),
                        Field.col("cashier_name", "By"))
                .fields(Field.col("return_number", "Return number"), Field.col("receipt_number", "Original receipt"), Field.datetime("created_at", "Date"),
                        Field.col("customer.name", "Customer"), Field.col("refund_method", "Refund method"), Field.money("refund_amount", "Refund"),
                        Field.col("reason", "Reason"),
                        Field.items("items", "Items", Field.col("product_name", "Item"), Field.col("quantity", "Qty", "number"),
                                Field.col("unit_price", "Price", "money"), Field.col("subtotal", "Subtotal", "money"))));

        r.add(new Page(Page.Kind.CRUD, "purchase-returns", "Purchase Returns").singular("Purchase return").endpoint("/purchase-returns")
                .ops("list", "read", "export").readOnly().titleKey("return_number").sort("created_at", "desc").search("Search purchase number")
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("return_number", "Return no."), Field.col("purchase_number", "Purchase"),
                        Field.col("supplier.name", "Supplier"), Field.col("item_count", "Items", "number"), Field.col("refund_amount", "Refund", "money"))
                .fields(Field.col("return_number", "Return number"), Field.col("purchase_number", "Purchase"), Field.datetime("created_at", "Date"),
                        Field.col("supplier.name", "Supplier"), Field.col("refund_method", "Refund method"), Field.money("refund_amount", "Refund"),
                        Field.col("reason", "Reason"),
                        Field.items("items", "Items", Field.col("product_name", "Item"), Field.col("quantity", "Qty", "number"),
                                Field.col("unit_cost", "Cost", "money"), Field.col("subtotal", "Subtotal", "money"))));
    }

    private static void debtPage(Registry r, String id, String title, String endpoint, String party, String partyLabel, Lookup lookup) {
        r.add(new Page(Page.Kind.CRUD, id, title).singular(title.substring(0, title.length() - 1)).endpoint(endpoint)
                .ops("list", "read", "export").readOnly().titleKey("reference_number")
                .tabs("status", "unpaid", Opt.pairs("unpaid", "Unpaid", "paid", "Paid", "", "All"))
                .search("Search " + partyLabel.toLowerCase() + " or reference")
                .filters(Field.lookup(party + "_id", partyLabel, lookup), Field.select("overdue", "Due", Opt.pairs("true", "Overdue only")))
                .columns(Field.col("transaction_date", "Date", "date"), Field.col("reference_number", "Reference"), Field.col(party + ".name", partyLabel),
                        Field.col("total_amount", "Total", "money"), Field.col("paid_amount", "Paid", "money"), Field.col("remaining_amount", "Remaining", "money"),
                        Field.col("due_date", "Due", "date"), Field.col("status", "Status", "badge"))
                .fields(Field.col("reference_number", "Reference"), Field.col(party + ".name", partyLabel), Field.date("transaction_date", "Date"),
                        Field.date("due_date", "Due date"), Field.money("total_amount", "Total"), Field.money("paid_amount", "Paid"),
                        Field.money("remaining_amount", "Remaining"), Field.col("status", "Status"),
                        Field.items("payments", "Payment history", Field.col("payment_date", "Date", "date"), Field.col("amount", "Amount", "money"),
                                Field.col("payment_method", "Method"), Field.col("note", "Note")))
                .rowActions(Action.call("pay", "Record payment", "POST", endpoint + "/{id}/payments").success("Payment recorded")
                        .when(row -> !"paid".equals(row.optString("status")))
                        .values((host, row) -> Json.obj("amount", row.opt("remaining_amount"), "payment_date", LocalDate.now().toString(), "payment_method", "cash"))
                        .form(Field.date("payment_date", "Date").req(), Field.money("amount", "Amount").req().min(1),
                                Field.select("payment_method", "Method", SALES_PAYMENT_METHODS).req(), Field.textarea("note", "Note"))));
    }
}
