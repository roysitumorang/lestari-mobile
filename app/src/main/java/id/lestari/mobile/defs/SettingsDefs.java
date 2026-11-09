package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.BRANCHES;
import static id.lestari.mobile.defs.Common.CATEGORIES;
import static id.lestari.mobile.defs.Common.CITIES;
import static id.lestari.mobile.defs.Common.DISCOUNT_TYPE;
import static id.lestari.mobile.defs.Common.GENDER;
import static id.lestari.mobile.defs.Common.PROVINCES;
import static id.lestari.mobile.defs.Common.ROLE;
import static id.lestari.mobile.defs.Common.SALES_PAYMENT_METHODS;
import static id.lestari.mobile.defs.Common.STAFF;
import static id.lestari.mobile.defs.Common.list;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Host;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import org.json.JSONObject;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings group: profile, store, online store … staff, attendance, support access, appearance (js/modules/settings.js). */
final class SettingsDefs {
    private SettingsDefs() {}

    private static Page settings(Registry r, String id, String title, String endpoint) {
        return r.add(new Page(Page.Kind.SETTINGS, id, title).endpoint(endpoint));
    }

    private static Map<String, Object> perPage(int n) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("per_page", n);
        return q;
    }

    /** Sign-ins and important changes (owner: /account/activity-logs, staff: /staff/{id}/activity-logs). */
    static void activityLog(Host host, String path, String title) {
        host.table(title, path, perPage(100), list(Field.col("created_at", "Time", "datetime"), Field.col("action", "Action", "badge"),
                Field.col("description", "Detail"), Field.col("ip_address", "IP")), "No activity");
    }

    /** Change password — also offered from the main menu. */
    static Action CHANGE_PASSWORD;


    /** (Re)builds the shared options and fields in the current language; called by Defs.build(). */
    static void init() {
        CHANGE_PASSWORD = Action.call("password", "Change password", "PUT", "/auth/password").noReload().success("Password changed")
                .form(Field.password("current_password", "Current password").req().w(12), Field.password("new_password", "New password").req().w(12),
                        Field.password("new_password_confirmation", "Repeat new password").req().w(12));
    }

    static void register(Registry r) {
        // ---------- profile ----------
        List<Field> profile = new ArrayList<>(list(
                Field.image("avatar_url", "Photo").w(12), Field.text("name", "Full name").req(), Field.text("username", "Username"),
                Field.email("email", "Email").ro().help("Contact support to change the login email."), Field.tel("phone", "Phone (e.g. +628…)"),
                Field.sw("phone_verified", "Phone verified").ro(),
                Field.number("birth_year", "Birth year").min(1900).max(Year.now().getValue()),
                Field.select("gender", "Gender", GENDER), Field.textarea("address", "Address"), Field.heading("Notifications")));
        String[][] channels = {{"app", "App"}, {"email", "Email"}, {"whatsapp", "WhatsApp"}};
        for (String[] ch : channels) {
            for (String topic : new String[]{"billing", "ppob", "promotions", "newsletter"}) {
                if (topic.equals("ppob") && !ch[0].equals("app")) continue;
                profile.add(Field.sw("notifications." + ch[0] + "." + topic, ch[1] + " · " + Character.toUpperCase(topic.charAt(0)) + topic.substring(1)).w(4));
            }
        }
        settings(r, "profile", "Profile", "/account/profile").fields(profile)
                .toolbar(CHANGE_PASSWORD,
                        Action.call("pin", "Change PPOB PIN", "PUT", "/account/ppob-pin").noReload().success("PIN changed")
                                .form(Field.password("password", "Account password").req().w(12),
                                        Field.password("new_pin", "New 6-digit PIN").noEncode().req().w(12)),
                        Action.custom("verify_phone", "Verify phone", (host, row, ctx) -> verifyPhone(host)),
                        Action.custom("two_factor", "Two-step verification", (host, row, ctx) -> twoFactor(host)),
                        Action.custom("activity", "Owner activity log", (host, row, ctx) -> activityLog(host, "/account/activity-logs", "Owner activity log")),
                        Action.call("deactivate", "Deactivate account", "POST", "/account/deactivation-requests").danger().success("Deactivation requested")
                                .confirm("Request deactivation of this account? Your data stays until support processes the request.").typeToConfirm("DEACTIVATE"))
                .afterSave((host, saved) -> host.refreshUser());

        // ---------- store ----------
        settings(r, "store", "Physical Store", "/settings/store")
                .fields(Field.image("logo_url", "Store logo"), Field.image("cover_url", "Cover photo"), Field.text("name", "Store name").req(),
                        Field.text("owner_name", "Owner name"), Field.tel("phone", "Store phone"), Field.text("motto", "Tagline"),
                        Field.select("business_type", "Business type", Opt.of("Pharmacy & beauty", "Workshop & car wash", "Phone & credit counter", "Laundry",
                                "Food & beverage", "Grocery / minimarket", "Fashion", "Building materials", "Services", "Other")),
                        Field.text("business_type_other", "Describe your business").showIf("business_type", "Other"),
                        Field.textarea("address", "Address"),
                        Field.select("country_code", "Country", Opt.pairs("ID", "Indonesia", "MY", "Malaysia", "SG", "Singapore", "TL", "Timor-Leste")).def("ID"),
                        Field.lookup("province_id", "Province", PROVINCES), Field.lookup("city_id", "City / regency", CITIES),
                        Field.text("postal_code", "Postal code"), Field.number("latitude", "Latitude"), Field.number("longitude", "Longitude"),
                        Field.heading("Accounting"),
                        Field.select("stock_valuation_method", "Stock valuation", Opt.pairs("default", "Last purchase price", "fifo", "FIFO", "lifo", "LIFO",
                                "average", "Weighted average")).def("average").help("Changing this recalculates cost of goods for future sales."),
                        Field.select("currency", "Currency", Opt.of("IDR", "MYR", "SGD", "USD", "AUD", "EUR")).def("IDR"),
                        Field.select("timezone", "Time zone", Opt.of("Asia/Jakarta", "Asia/Makassar", "Asia/Jayapura")).def("Asia/Jakarta"))
                .toolbar(Action.download("qr", "Store QR code", "/settings/store/qr-code", "store-qr.png"));

        settings(r, "online-store", "Online Store", "/settings/online-store")
                .fields(Field.sw("is_enabled", "Online store is open").w(12),
                        Field.text("subdomain", "Store address").help("Letters, numbers and dashes. Becomes <subdomain>.yourdomain."),
                        Field.email("store_email", "Store email"), Field.textarea("description", "About the store"),
                        Field.text("facebook", "Facebook username").w(4), Field.text("twitter", "X / Twitter username").w(4),
                        Field.text("instagram", "Instagram username").w(4), Field.sw("seo_enabled", "Allow search engines"),
                        Field.sw("pwa_enabled", "Installable store app (PWA)"), Field.sw("use_physical_stock", "Sell from physical-store stock"),
                        Field.sw("notify_owner", "Notify owner of new orders").def(true), Field.multiLookup("notify_staff_ids", "Also notify staff", STAFF).w(12));

        settings(r, "link-page", "Link Page", "/settings/link-page")
                .desc("A single link (bio link) that lists your catalog, WhatsApp, marketplaces and socials.")
                .fields(Field.sw("is_enabled", "Link page is live").w(12), Field.text("username", "Username").req(), Field.text("slogan", "Slogan"),
                        Field.color("background_color", "Background color").def("#0f9d76"), Field.sw("show_watermark", "Show \"powered by\" footer").def(true),
                        Field.items("links", "Links",
                                Field.select("type", "Type", Opt.of("catalog", "whatsapp", "location", "tokopedia", "shopee", "grabfood", "gofood", "shopeefood",
                                        "tiktok", "instagram", "facebook", "twitter", "youtube", "custom")),
                                Field.text("label", "Label"), Field.text("url", "URL"), Field.sw("is_active", "On")))
                .below(Page.Below.fromEndpoint("Recent visitors", "/settings/link-page/visitors", "No visitors yet",
                        Field.col("ip_address", "IP address"), Field.col("visited_at", "Time", "datetime")).numbered());

        settings(r, "qr-order", "QR Order", "/settings/qr-order")
                .desc("Customers scan a QR code at the table or kiosk to browse the menu and order.")
                .fields(Field.sw("is_enabled", "QR ordering is on").w(12),
                        Field.select("order_expiry_hours", "QR code valid for", Opt.pairs(3, "3 hours", 6, "6 hours", 12, "12 hours", 24, "24 hours")).def(3),
                        Field.select("payment_timing", "Customers pay", Opt.pairs("pay_later", "Later, at the cashier", "pay_now", "Immediately when ordering")).def("pay_later"),
                        Field.sw("qris_enabled", "Accept QRIS"), Field.sw("cashier_payment_enabled", "Allow paying at the cashier").def(true),
                        Field.sw("addons_enabled", "Show add-ons / modifiers"),
                        Field.select("menu_layout", "Menu layout", Opt.pairs("tabs", "All menu with tabs", "by_category", "One category at a time")).def("tabs"),
                        Field.multiLookup("category_ids", "Categories shown (empty = all)", CATEGORIES).w(12),
                        Field.sw("table_ordering_enabled", "Table ordering"), Field.sw("kiosk_enabled", "Self-service kiosk"),
                        Field.sw("notify_owner", "Notify owner of new orders").def(true), Field.multiLookup("notify_staff_ids", "Also notify staff", STAFF).w(12))
                .toolbar(Action.download("menu_book", "Download menu book", "/settings/qr-order/menu-book", "menu.pdf"));

        // ---------- payment ----------
        r.add(new Page(Page.Kind.CRUD, "payment-methods", "Payment Methods").singular("Payment method").endpoint("/payment-methods")
                .desc("Methods shown to the cashier, with optional MDR fee per method.")
                .ops("list", "create", "read", "update", "delete")
                .columns(Field.col("name", "Name"), Field.col("type", "Type", "badge"), Field.col("mdr_type", "MDR type"), Field.col("mdr_value", "MDR", "number"),
                        Field.col("is_active", "Active", "boolean"))
                .fields(Field.text("name", "Name").req(), Field.select("type", "Type", SALES_PAYMENT_METHODS).req(),
                        Field.text("code", "Code").help("Optional short code used in exports."), Field.select("mdr_type", "MDR fee type", DISCOUNT_TYPE),
                        Field.number("mdr_value", "MDR fee").min(0), Field.sw("charge_fee_to_customer", "Add the fee to the customer's bill"),
                        Field.items("edc_card_rates", "EDC card rates",
                                Field.select("card_brand", "Card", Opt.of("visa", "mastercard", "amex", "jcb", "unionpay", "debit", "flazz", "other")),
                                Field.percent("mdr_percent", "MDR %"))
                                .showIf("type", "debit", "credit_card").help("Different MDR per card brand on the same EDC machine."),
                        Field.sw("is_active", "Active").def(true)));

        settings(r, "default-tax", "Default Tax", "/settings/default-tax")
                .fields(Field.sw("is_enabled", "Apply a default tax to every sale").w(12), Field.percent("rate", "Default tax rate").min(0).max(100),
                        Field.sw("is_inclusive", "Prices already include tax"));
        settings(r, "service-charge", "Service Charge", "/settings/service-charge").plugin("business")
                .fields(Field.sw("is_enabled", "Add a service charge").w(12), Field.text("name", "Label on receipt").def("Service charge"),
                        Field.percent("rate", "Rate").min(0).max(100), Field.sw("apply_before_tax", "Calculate before tax"));
        settings(r, "rounding", "Rounding", "/settings/rounding").plugin("business")
                .fields(Field.sw("is_enabled", "Round the total").w(12),
                        Field.select("mode", "Direction", Opt.pairs("nearest", "Nearest", "up", "Up", "down", "Down")).def("nearest"),
                        Field.select("multiple", "Round to multiples of", Opt.pairs(50, "Rp 50", 100, "Rp 100", 500, "Rp 500", 1000, "Rp 1000")).def(100));

        Page storage = settings(r, "storage", "Storage", "/settings/storage").readOnly()
                .fields(Field.text("plan", "Storage plan"), Field.number("used_mb", "Used (MB)"), Field.number("quota_mb", "Quota (MB)"),
                        Field.number("transaction_count", "Transactions stored"), Field.date("expires_at", "Extra storage active until"))
                .toolbar(Action.call("buy", "Buy more storage", "POST", "/settings/storage/purchases").success("Storage purchased")
                        .form(Field.select("package", "Package", Opt.pairs("1gb_monthly", "+1 GB / month", "5gb_monthly", "+5 GB / month", "10gb_monthly", "+10 GB / month")).req(),
                                Field.select("payment_method", "Pay with", Opt.pairs("coin", "Premium coin balance")).def("coin")));
        storage.readOnly = true;

        // Custom screens (Android layer): POST /settings/reset-data/otp, POST /settings/reset-data; GET /plugins, POST /plugins/{code}/subscribe|trial
        r.add(new Page(Page.Kind.CUSTOM, "reset-data", "Reset Data").desc("Permanently delete store data. This cannot be undone."));
        r.add(new Page(Page.Kind.CUSTOM, "plugins", "Plugins").desc("Add-ons that unlock extra back-office features."));

        List<Field> receipt = new ArrayList<>(list(Field.image("logo_url", "Receipt logo").w(12), Field.textarea("header_note", "Header text").w(6),
                Field.textarea("footer_note", "Footer text").w(6), Field.heading("Show on receipt")));
        String[][] switches = {{"show_logo", "Store logo"}, {"auto_print", "Print automatically after payment"}, {"show_receipt_number", "Receipt number"},
                {"show_receipt_code", "Receipt barcode"}, {"show_sequence_number", "Daily sequence number"}, {"show_unit", "Unit next to quantity"},
                {"show_customer_address", "Customer address"}, {"show_total_quantity", "Total quantity"}, {"show_signature", "Signature column"},
                {"show_price_type", "Price type"}, {"separate_admin_fee", "Show admin fee separately"}, {"show_points", "Customer points"},
                {"hide_tax_percentage", "Hide tax percentage"}, {"show_feedback_link", "Feedback link"}, {"show_qr_code", "Store QR code"}};
        for (String[] s : switches) receipt.add(Field.sw(s[0], s[1]).w(4));
        settings(r, "receipt", "Receipt", "/settings/receipt").fields(receipt);

        // ---------- staff ----------
        List<Opt> permissions = Opt.humanized("dashboard", "products", "stock", "customers", "suppliers", "discounts", "taxes", "sales", "purchases",
                "finance", "reports", "debts", "returns", "settings", "staff", "branches", "ppob", "online_store", "void_transaction", "edit_price", "give_discount");
        r.add(new Page(Page.Kind.CRUD, "staff", "Staff").singular("Staff member").endpoint("/staff")
                .desc("Accounts for administrators, managers and cashiers. Each staff account is billed per your plan.")
                .ops("list", "create", "read", "update", "delete", "bulk_delete")
                .columns(Field.col("name", "Name"), Field.col("email", "Email"), Field.col("phone", "Phone"), Field.select("role", "Role", ROLE),
                        Field.col("status", "Status", "badge"), Field.col("last_login_at", "Last sign-in", "datetime"))
                .fields(Field.text("name", "Full name").req(), Field.email("email", "Email").req(),
                        Field.password("password", "Password").help("Leave blank when editing to keep the current password."),
                        Field.tel("phone", "Phone"), Field.select("role", "Role", ROLE).req().def("cashier"),
                        Field.password("ppob_pin", "PPOB PIN").noEncode().help("Optional. Leave blank to keep."),
                        Field.lookup("branch_id", "Branch", BRANCHES), Field.textarea("address", "Address"))
                .rowActions(
                        Action.call("permissions", "Access rights", "PUT", "/staff/{id}/permissions").success("Access rights saved")
                                .values((host, row) -> host.api().get("/staff/" + row.optString("id") + "/permissions").optJSONObject("data"))
                                .form(Field.multi("permissions", "Allowed", permissions).w(12)),
                        Action.call("resend", "Resend invitation email", "POST", "/staff/{id}/resend-invitation").confirm("Send the confirmation email again?")
                                .success("Invitation sent").when(row -> "invited".equals(row.optString("status"))),
                        Action.custom("log", "Activity log", (host, row, ctx) -> activityLog(host, "/staff/" + row.optString("id") + "/activity-logs",
                                I18n.t("Activity log") + " · " + row.optString("name")))));

        r.add(new Page(Page.Kind.REPORT, "attendance", "Staff Attendance").endpoint("/staff-attendances").plugin("attendance").preset("this_month")
                .filters(Field.lookup("staff_id", "Staff", STAFF))
                .summary(Field.col("attendance_count", "Check-ins", "number"), Field.col("late_count", "Late", "number"), Field.col("total_hours", "Hours worked", "number"))
                .columns(Field.col("date", "Date", "date"), Field.col("staff_name", "Staff"), Field.col("clock_in_at", "In", "datetime"),
                        Field.col("clock_out_at", "Out", "datetime"), Field.col("hours", "Hours", "number"), Field.col("status", "Status", "badge"),
                        Field.col("location", "Location"))
                .detail(new Page.Detail("/staff-attendances/{id}", "Attendance").fields(Field.col("staff_name", "Staff"), Field.datetime("clock_in_at", "In"),
                        Field.image("clock_in_photo_url", "Photo in"), Field.datetime("clock_out_at", "Out"), Field.image("clock_out_photo_url", "Photo out"),
                        Field.col("location", "Location"), Field.col("note", "Note"))));

        r.add(new Page(Page.Kind.CRUD, "help-access", "Support Access").singular("Support access").endpoint("/help-access")
                .desc("Let our support team sign in to an account for a limited time to help you.")
                .ops("list", "create", "read").titleKey("account_email")
                .columns(Field.col("created_at", "Created", "datetime"), Field.col("platform", "Platform"), Field.col("account_email", "Account"),
                        Field.col("expires_at", "Expires", "datetime"), Field.col("status", "Status", "badge"))
                .fields(Field.select("platform", "Where", Opt.pairs("web", "Website (back office)", "app", "Cashier app")).req().def("web"),
                        Field.lookup("account_id", "Account", new Lookup("/help-access/accounts", "id", "label")).req(),
                        Field.select("duration_hours", "Duration", Opt.pairs(1, "1 hour", 6, "6 hours", 24, "24 hours", 72, "3 days")).req().def(24),
                        Field.textarea("description", "What do you need help with?").req(),
                        Field.text("account_email", "Account").noForm(), Field.datetime("expires_at", "Expires").noForm(), Field.text("status", "Status").noForm())
                .rowActions(Action.call("revoke", "Revoke access", "POST", "/help-access/{id}/revoke").danger().confirm("Revoke this support access now?")
                        .success("Access revoked").when(row -> "active".equals(row.optString("status")))));

        settings(r, "display", "Back Office Appearance", "/settings/display")
                .fields(Field.select("color_theme", "Color theme", Opt.pairs("emerald", "Emerald", "ivory", "White ivory", "charcoal", "Black charcoal")).def("emerald"),
                        Field.sw("show_breadcrumbs", "Show breadcrumbs").def(true),
                        Field.select("language", "Language", Opt.pairs("en", "English", "id", "Bahasa Indonesia")).def("en"))
                .afterSave((host, saved) -> host.applyDisplay(saved));
    }

    /** Send an OTP to the phone, then confirm it. */
    static void verifyPhone(Host host) {
        final String[] phone = {""};
        Host.FormSpec send = new Host.FormSpec();
        send.title = "Verify phone number";
        send.submitText = "Send code";
        send.fields = list(Field.tel("phone", "Phone (+628…)").req().w(12),
                Field.select("channel", "Send code by", Opt.pairs("whatsapp", "WhatsApp", "sms", "SMS")).def("whatsapp").w(12));
        send.values = Json.obj("phone", Json.str(host.user(), "phone"));
        send.onSubmit = v -> {
            phone[0] = v.optString("phone");
            host.api().post("/account/phone-verification", v);
        };
        send.onDone = () -> {
            Host.FormSpec confirm = new Host.FormSpec();
            confirm.title = "Enter verification code";
            confirm.submitText = "Verify";
            confirm.intro = I18n.t("We sent a code to {to}.", "to", phone[0]);
            confirm.fields = list(Field.text("code", "Code").req().w(12));
            confirm.onSubmit = v -> host.api().post("/account/phone-verification/confirm", Json.obj("phone", phone[0], "code", v.optString("code")));
            confirm.onDone = () -> {
                host.toast("Phone verified");
                host.go("profile");
            };
            host.form(confirm);
        };
        host.form(send);
    }

    /** Turn authenticator-app verification on (setup → enable → recovery codes) or off (password). */
    static void twoFactor(Host host) {
        if (host.user().optBoolean("two_factor_enabled")) {
            Host.FormSpec off = new Host.FormSpec();
            off.title = "Turn off two-step verification";
            off.submitText = "Turn off";
            off.fields = list(Field.password("password", "Password").req().w(12));
            off.onSubmit = v -> host.api().post("/account/two-factor/disable", v);
            off.onDone = () -> {
                host.toast("Two-step verification turned off");
                host.refreshUser();
            };
            host.form(off);
            return;
        }
        host.run(() -> host.api().post("/account/two-factor/setup", null).optJSONObject("data"), setup -> {
            if (setup == null) return;
            final JSONObject[] enabled = {null};
            Host.FormSpec f = new Host.FormSpec();
            f.title = "Two-step verification";
            f.submitText = "Enable";
            f.intro = I18n.t("Add this key to Google Authenticator or a similar app, then enter the 6-digit code it shows.") + "\n\n" + I18n.t("Key") + ": " + setup.optString("secret");
            f.linkLabel = "Open in authenticator app";
            f.linkUrl = setup.optString("otpauth_url");
            f.fields = list(Field.text("code", "6-digit code").req().w(12));
            f.onSubmit = v -> enabled[0] = host.api().post("/account/two-factor/enable", Json.obj("code", v.optString("code"))).optJSONObject("data");
            f.onDone = () -> {
                StringBuilder codes = new StringBuilder();
                for (Object c : Json.values(enabled[0] == null ? null : enabled[0].opt("recovery_codes"))) codes.append(c).append('\n');
                host.message(I18n.t("Recovery codes"), I18n.t("Two-step verification is on. Keep these recovery codes somewhere safe:") + "\n\n" + codes);
                host.refreshUser();
            };
            host.form(f);
        });
    }
}
