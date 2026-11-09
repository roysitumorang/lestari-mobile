package id.lestari.mobile.defs;

import static id.lestari.mobile.core.Registry.sub;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Registry;

/** Builds every page definition and the navigation menu (js/menu.js). */
public final class Defs {
    private Defs() {}

    private static Registry registry;

    public static synchronized Registry get() {
        if (registry == null) registry = build();
        return registry;
    }

    /** "Change password" action shared by the profile page and the main menu. */
    public static Action changePassword() {
        return SettingsDefs.CHANGE_PASSWORD;
    }

    /** Forget the definitions so the next {@link #get()} rebuilds them in the current language. */
    public static synchronized void reset() {
        registry = null;
    }

    static Registry build() {
        Common.init();
        TransactionDefs.init();
        SettingsDefs.init();
        Registry r = new Registry();
        DashboardDefs.register(r);
        DatabaseDefs.register(r);
        TransactionDefs.register(r);
        ReportDefs.register(r);
        BranchDefs.register(r);
        SettingsDefs.register(r);
        CommerceDefs.register(r);

        r.group("Reward Missions", "missions");
        r.group("Billing", "billing");
        r.group("Dashboard", "dashboard");
        r.group("Database", "products", "stock", "categories",
                sub("Customers & Suppliers", "customers", "customer-types", "suppliers"),
                sub("Discounts, Taxes & Fees", "discounts", "taxes", "fees", "cash-drawers"),
                sub("Marketing", "promotions", "points", "commissions"),
                "stock-opnames");
        r.group("Transactions", "sales", "purchases", "finance");
        r.group("Reports", "report-profit-loss", "report-cash-flow",
                sub("Sales", "report-daily-sales", "report-sales-transactions", "report-sales-by-product", "report-sales-by-category"),
                sub("Purchases", "report-daily-purchases", "report-purchase-transactions"),
                sub("Points", "report-customer-points", "report-point-redemptions", "report-point-earnings"),
                sub("Debts", "payables", "receivables"),
                "report-capital",
                sub("Returns", "sales-returns", "purchase-returns"),
                "report-shifts",
                sub("Fees", "report-fees-sales", "report-fees-purchases", "report-mdr"),
                "report-taxes", "report-ppob", "report-visitors",
                sub("Promotions", "report-promotion-transactions", "report-promotion-products"),
                "report-store-ratings");
        r.group("Branches", "branch-billing", "branch-dashboard", "branch-products", "stock-transfers",
                sub("Transfer Reports", "report-transfers", "report-transfer-confirmations"),
                "branches");
        r.group("Settings", "profile",
                sub("Store", "store", "online-store", "link-page", "qr-order"),
                sub("Payment", "payment-methods", "default-tax", "service-charge", "rounding"),
                "storage", "reset-data", "plugins", "receipt",
                sub("Staff", "staff", "attendance"),
                "help-access", "display");
        r.group("E-Catalog", "catalog-orders", "catalog-shipping", "catalog-accounts");
        r.group("PPOB", "ppob-deposits", "ppob-prepaid", "ppob-postpaid");
        r.group("Referral Program", "referral");
        r.group("Integrations", "integrations");
        r.group("Business Loans", "loans");
        r.group("Notifications", "notifications");
        return r;
    }
}
