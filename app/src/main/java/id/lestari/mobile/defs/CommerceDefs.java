package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.CITIES;
import static id.lestari.mobile.defs.Common.PAYMENT_CHANNELS;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Format;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** E-catalog (online orders), PPOB, referral, integrations, loans and notifications (js/modules/commerce.js). */
final class CommerceDefs {
    private CommerceDefs() {}

    /** PATCH /catalog/orders/{id}/status with a fixed status (merged with any form values). */
    private static Action status(String id, String label, String to, Action.When when) {
        return Action.call(id, label, "PATCH", "/catalog/orders/{id}/status").success("Order updated").body(Json.obj("status", to)).when(when);
    }

    static void register(Registry r) {
        List<Opt> orderStatuses = Opt.pairs("", "All", "unpaid", "Unpaid", "awaiting_confirmation", "Awaiting confirmation", "payment_rejected", "Payment rejected",
                "processing", "Processing", "shipped", "Shipped", "delivered", "Delivered", "completed", "Completed", "returned", "Returned", "cancelled", "Cancelled");
        r.add(new Page(Page.Kind.CRUD, "catalog-orders", "Online Orders").singular("Order").endpoint("/catalog/orders")
                .ops("list", "read", "export").readOnly().titleKey("order_number").sort("created_at", "desc")
                .tabs("status", "", orderStatuses).search("Search name or order number")
                .filters(Field.select("payment_method", "Payment", Opt.pairs("bank_transfer", "Bank transfer / e-wallet", "direct", "Pay on pickup / COD")),
                        Field.select("sort_by", "Sort by", Opt.pairs("latest", "Newest", "grand_total", "Order value")))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("order_number", "Order"), Field.col("customer_name", "Customer"),
                        Field.col("shipping_method", "Delivery"), Field.col("payment_method", "Payment"), Field.col("grand_total", "Total", "money"),
                        Field.col("status", "Status", "badge"))
                .fields(Field.col("order_number", "Order number"), Field.datetime("created_at", "Date"), Field.col("status", "Status"),
                        Field.col("customer_name", "Customer"), Field.col("customer_phone", "Phone"), Field.col("shipping_address", "Ship to"),
                        Field.col("shipping_method", "Delivery"), Field.col("courier", "Courier"), Field.col("tracking_number", "Tracking number"),
                        Field.col("payment_method", "Payment"), Field.image("payment_proof_url", "Payment proof"),
                        Field.items("items", "Items", Field.col("product_name", "Item"), Field.col("quantity", "Qty", "number"),
                                Field.col("unit_price", "Price", "money"), Field.col("subtotal", "Subtotal", "money")),
                        Field.money("subtotal", "Subtotal"), Field.money("shipping_fee", "Shipping"), Field.money("grand_total", "Total"),
                        Field.col("customer_note", "Customer note"))
                .rowActions(
                        status("confirm_payment", "Confirm payment", "processing", row -> "awaiting_confirmation".equals(row.optString("status"))),
                        status("reject_payment", "Reject payment", "payment_rejected", row -> "awaiting_confirmation".equals(row.optString("status")))
                                .form(Field.textarea("reason", "Reason shown to the customer").req()),
                        status("ship", "Mark as shipped", "shipped", row -> "processing".equals(row.optString("status")))
                                .form(Field.text("courier", "Courier"), Field.text("tracking_number", "Tracking number")),
                        status("deliver", "Mark as delivered", "delivered", row -> "shipped".equals(row.optString("status"))),
                        status("complete", "Complete", "completed", row -> Arrays.asList("delivered", "processing").contains(row.optString("status"))),
                        status("cancel", "Cancel order", "cancelled", row -> Arrays.asList("unpaid", "awaiting_confirmation", "processing").contains(row.optString("status")))
                                .danger().form(Field.textarea("reason", "Reason").req()),
                        Action.download("invoice", "Download invoice", "/catalog/orders/{id}/invoice", "order.pdf")));

        r.add(new Page(Page.Kind.SETTINGS, "catalog-shipping", "Shipping & Payment").endpoint("/settings/catalog")
                .desc("How online-store customers pay and receive their orders.")
                .fields(Field.heading("Payment"), Field.sw("bank_transfer_enabled", "Bank transfer & e-wallet"),
                        Field.sw("direct_payment_enabled", "Pay directly (on pickup / COD)"),
                        Field.heading("Delivery"), Field.sw("takeaway_enabled", "Pick up at store"), Field.sw("cod_enabled", "Cash on delivery"),
                        Field.sw("courier_enabled", "Courier services"),
                        couriers().showIf("courier_enabled", "true"),
                        Field.sw("own_courier_enabled", "Own delivery staff"),
                        Field.select("own_courier_mode", "Own delivery fee by", Opt.pairs("city", "City", "radius", "Distance")).showIf("own_courier_enabled", "true"),
                        Field.items("own_courier_city_rates", "Fee per city", Field.lookup("city_id", "City", CITIES), Field.money("fee", "Fee"))
                                .showIf("own_courier_mode", "city"),
                        Field.items("own_courier_radius_rates", "Fee per distance", Field.number("min_km", "From km"), Field.number("max_km", "To km"),
                                Field.money("fee", "Fee")).showIf("own_courier_mode", "radius")));

        r.add(new Page(Page.Kind.CRUD, "catalog-accounts", "Payout Accounts").singular("Account").endpoint("/catalog/payment-accounts")
                .desc("Bank, e-wallet and QRIS accounts shown to online customers for transfers.")
                .ops("list", "create", "read", "update", "delete").titleKey("provider")
                .columns(Field.col("type", "Type", "badge"), Field.col("provider", "Bank / e-wallet"), Field.col("account_name", "Account name"),
                        Field.col("account_number", "Number"), Field.col("is_active", "Active", "boolean"))
                .fields(Field.select("type", "Type", Opt.pairs("bank", "Bank account", "e_wallet", "E-wallet", "qris", "QRIS")).req().def("bank"),
                        Field.text("provider", "Bank / e-wallet name").req().ph("BCA, Mandiri, GoPay, OVO…"),
                        Field.text("account_name", "Account holder").req(),
                        Field.text("account_number", "Account / phone number").showIf("type", "bank", "e_wallet"),
                        Field.image("qris_image_url", "QRIS image").w(12).showIf("type", "qris"),
                        Field.sw("is_active", "Active").def(true)));

        // ---------- PPOB ----------
        List<Opt> topUps = new ArrayList<>();
        for (int n : new int[]{100000, 200000, 250000, 300000, 500000, 1000000}) topUps.add(new Opt(n, Format.money(n)));
        r.add(new Page(Page.Kind.REPORT, "ppob-deposits", "PPOB Deposit").endpoint("/ppob/deposits").preset("last_30_days").noExport()
                .desc("Prepaid balance used to sell phone credit, data, electricity tokens and bill payments. Minimum top-up Rp100.000, maximum balance Rp2.000.000.")
                .search("Search history")
                .summary(Field.col("balance", "Current balance", "money"), Field.col("transaction_total", "All PPOB transactions", "money"))
                .columns(Field.col("created_at", "Date", "datetime"), Field.col("reference_id", "Reference"), Field.col("type", "Type", "badge"),
                        Field.col("payment_method", "Method"), Field.col("amount", "Amount", "money"), Field.col("balance_after", "Balance after", "money"),
                        Field.col("status", "Status", "badge"))
                .toolbar(Action.call("topup", "Top up balance", "POST", "/ppob/deposits").success("Top-up created").after(Action.OPEN_PAYMENT)
                        .form(Field.select("amount", "Amount", topUps).req().def(100000), Field.select("payment_method", "Pay with", PAYMENT_CHANNELS).req(),
                                Field.text("voucher_code", "Voucher (optional)")))
                .rowActions(
                        Action.call("proof", "Upload transfer proof", "POST", "/ppob/deposits/{id}/payment-proof").upload().success("Proof uploaded")
                                .when(row -> "pending".equals(row.optString("status")) && "manual_transfer".equals(row.optString("payment_method")))
                                .form(Field.file("file", "Transfer receipt").accept("image/*,application/pdf").req().w(12)),
                        Action.call("cancel", "Cancel", "POST", "/ppob/deposits/{id}/cancel").confirm("Cancel this top-up?").success("Top-up cancelled")
                                .when(row -> "pending".equals(row.optString("status")))));

        ppobPrice(r, "ppob-prepaid", "PPOB Prepaid Prices", "/ppob/prices/prepaid",
                Opt.humanized("pulsa", "data", "pln_token", "e_money", "game_voucher", "streaming"),
                Arrays.asList(Field.col("category", "Category", "badge"), Field.col("provider", "Provider"), Field.col("product_name", "Product"),
                        Field.col("base_price", "Cost", "money"), Field.col("selling_price", "Your price", "money"), Field.col("is_active", "On sale", "boolean")),
                Arrays.asList(Field.text("product_name", "Product").ro().w(12), Field.money("base_price", "Cost").ro(),
                        Field.money("selling_price", "Your selling price").req().min(0), Field.sw("is_active", "On sale").def(true)));
        ppobPrice(r, "ppob-postpaid", "PPOB Postpaid Fees", "/ppob/prices/postpaid",
                Opt.humanized("pln_postpaid", "pdam", "bpjs", "telkom", "gas_postpaid", "multifinance", "internet_tv"),
                Arrays.asList(Field.col("category", "Category", "badge"), Field.col("product_name", "Biller"), Field.col("base_admin_fee", "Base admin fee", "money"),
                        Field.col("admin_fee", "Your admin fee", "money"), Field.col("is_active", "On sale", "boolean")),
                Arrays.asList(Field.text("product_name", "Biller").ro().w(12), Field.money("base_admin_fee", "Base admin fee").ro(),
                        Field.money("admin_fee", "Your admin fee").req().min(0), Field.sw("is_active", "On sale").def(true)));

        // ---------- referral ----------
        r.add(new Page(Page.Kind.SETTINGS, "referral", "Referral Program").endpoint("/referrals").readOnly()
                .desc("Invite other businesses and earn rewards when they subscribe.")
                .fields(Field.text("code", "Your referral code"), Field.text("share_url", "Share link"), Field.number("referred_count", "Businesses referred"),
                        Field.money("reward_balance", "Reward balance"))
                .toolbar(
                        Action.call("create", "Create referral code", "POST", "/referrals/code").success("Referral code created")
                                .when(d -> d.optString("code", "").isEmpty() || d.isNull("code"))
                                .form(Field.text("code", "Preferred code (optional)").w(12)),
                        Action.call("withdraw", "Move rewards to coins", "POST", "/referrals/rewards/transfer-to-coin")
                                .confirm("Move your whole reward balance to premium coins?").success("Rewards moved to coins"))
                .below(Page.Below.fromData("Referred businesses", "referred_businesses", "No referrals yet",
                        Field.col("store_name", "Store"), Field.col("joined_at", "Joined", "date"), Field.col("status", "Status", "badge"),
                        Field.col("reward_amount", "Reward", "money"))));

        // Custom screens (Android layer): GET /integrations, POST /integrations/{code}/activate|deactivate;
        // GET /loans/providers, POST /loans/applications
        r.add(new Page(Page.Kind.CUSTOM, "integrations", "Integrations").desc("Connect payment providers and accounting software."));
        r.add(new Page(Page.Kind.CUSTOM, "loans", "Business Loans").desc("Working-capital loans from our lending partners, based on your sales history."));

        // ---------- notifications ----------
        r.add(new Page(Page.Kind.CRUD, "notifications", "Notifications").singular("Notification").endpoint("/notifications")
                .ops("list", "read").readOnly().titleKey("title").sort("created_at", "desc")
                .tabs("source", "", Opt.pairs("", "All", "back_office", "Back office", "online_store", "Online store"))
                .filters(Field.date("start_date", "From"), Field.date("end_date", "To"))
                .columns(Field.col("created_at", "Time", "datetime"), Field.col("title", "Title"), Field.col("message", "Message"),
                        Field.col("read_at", "Read").render((v, row) -> Json.truthy(v) ? "✓ Read" : "New"))
                .fields(Field.col("title", "Title"), Field.col("message", "Message"), Field.datetime("created_at", "Time"), Field.col("source", "Source", "badge"),
                        Field.col("link_route", "Related page"), Field.image("attachment_url", "Attachment"))
                .toolbar(Action.call("read_all", "Mark all as read", "POST", "/notifications/read-all").success("All marked as read")
                        .after((host, res, row) -> host.refreshBadge()))
                .rowActions(
                        Action.call("read", "Mark as read", "POST", "/notifications/{id}/read").success("Marked as read")
                                .when(row -> !Json.truthy(row.opt("read_at"))).after((host, res, row) -> host.refreshBadge()),
                        Action.custom("open", "Open related page", (host, row, ctx) -> host.go(row.optString("link_route")))
                                .when(row -> Json.truthy(row.opt("link_route")))));
    }

    private static Field couriers() {
        List<Opt> o = new ArrayList<>();
        for (String c : new String[]{"anteraja", "tiki", "esl", "jnt", "jne", "lion", "ncs", "pahala", "pos", "rpx", "sicepat", "wahana"}) {
            o.add(new Opt(c, c.toUpperCase()));
        }
        return Field.multi("couriers", "Couriers", o).w(12);
    }

    private static void ppobPrice(Registry r, String id, String title, String endpoint, List<Opt> categories, List<Field> columns, List<Field> fields) {
        r.add(new Page(Page.Kind.CRUD, id, title).singular("Price").endpoint(endpoint).ops("list", "update", "import", "export").titleKey("product_name")
                .search("Search product name").filters(Field.select("category", "Category", categories)).columns(columns).fields(fields));
    }
}
