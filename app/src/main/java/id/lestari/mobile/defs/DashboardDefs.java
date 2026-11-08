package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.COIN;
import static id.lestari.mobile.defs.Common.PAYMENT_CHANNELS;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

/** Dashboard, reward missions and billing (js/modules/dashboard.js). */
final class DashboardDefs {
    private DashboardDefs() {}

    static void register(Registry r) {
        // Custom screens (Android layer): GET /dashboard, GET /missions, POST /missions/{id}/claim
        r.add(new Page(Page.Kind.CUSTOM, "dashboard", "Dashboard").desc("How the store is doing on the selected day."));
        r.add(new Page(Page.Kind.CUSTOM, "missions", "Reward Missions").desc("Complete tasks to earn premium coins and trials."));

        r.add(new Page(Page.Kind.REPORT, "billing", "Billing & Subscription")
                .endpoint("/billing/invoices").preset("this_year").noExport()
                .desc("Your plan, premium coin balance and invoices.")
                .filters(Field.select("status", "Status", Opt.of("pending", "paid", "expired", "cancelled")))
                .summary(Field.col("plan_name", "Current plan"), Field.col("plan_expires_at", "Active until", "date"),
                        Field.col("coin_balance", "Premium coin balance", "money"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("invoice_number", "Invoice"), Field.col("description", "For"),
                        Field.col("payment_method", "Method"), Field.col("amount", "Amount", "money"), Field.col("status", "Status", "badge"))
                .toolbar(
                        Action.call("subscribe", "Renew / upgrade plan", "POST", "/billing/subscriptions").success("Invoice created").after(Action.OPEN_PAYMENT)
                                .form(Field.select("plan", "Plan", Opt.pairs("pro_monthly", "Pro · monthly", "pro_quarterly", "Pro · 3 months", "pro_yearly", "Pro · yearly")).req(),
                                        Field.select("payment_method", "Pay with", Opt.concat(COIN, PAYMENT_CHANNELS)).req(),
                                        Field.text("promo_code", "Promo code")),
                        Action.call("topup", "Top up coins", "POST", "/billing/coin-top-ups").success("Top-up created").after(Action.OPEN_PAYMENT)
                                .form(Field.money("amount", "Amount").req().min(10000), Field.select("payment_method", "Pay with", PAYMENT_CHANNELS).req(),
                                        Field.text("promo_code", "Promo code")),
                        Action.call("voucher", "Redeem voucher", "POST", "/billing/vouchers/redeem").success("Voucher redeemed")
                                .form(Field.text("code", "Voucher or token code").req().w(12)))
                .rowActions(
                        Action.custom("pay", "Pay now", (host, row, ctx) -> host.openUrl(row.optString("payment_url")))
                                .when(row -> "pending".equals(row.optString("status")) && !row.optString("payment_url", "").isEmpty() && !row.isNull("payment_url")),
                        Action.call("proof", "Upload transfer proof", "POST", "/billing/invoices/{id}/payment-proof").upload().success("Proof uploaded")
                                .when(row -> "pending".equals(row.optString("status")) && "manual_transfer".equals(row.optString("payment_method")))
                                .form(Field.file("file", "Transfer receipt (image/PDF)").accept("image/*,application/pdf").req().w(12),
                                        Field.text("sender_account_name", "Sender account name")),
                        Action.call("cancel", "Cancel", "POST", "/billing/invoices/{id}/cancel").confirm("Cancel this invoice?").success("Invoice cancelled")
                                .when(row -> "pending".equals(row.optString("status"))),
                        Action.download("pdf", "Download invoice", "/billing/invoices/{id}/pdf", "invoice.pdf")
                                .when(row -> "paid".equals(row.optString("status")))));
    }
}
