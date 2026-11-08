package id.lestari.mobile.defs;

import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.Lookup;
import id.lestari.mobile.core.Opt;

import java.util.Arrays;
import java.util.List;

/** Shared lookups and option lists (js/modules/_common.js). */
final class Common {
    private Common() {}

    static final Lookup CATEGORIES = new Lookup("/categories");
    static final Lookup PRODUCTS = new Lookup("/products");
    static final Lookup CUSTOMERS = new Lookup("/customers");
    static final Lookup CUSTOMER_TYPES = new Lookup("/customer-types");
    static final Lookup SUPPLIERS = new Lookup("/suppliers");
    static final Lookup STAFF = new Lookup("/staff");
    static final Lookup BRANCHES = new Lookup("/branches");
    static final Lookup CASH_DRAWERS = new Lookup("/cash-drawers");
    static final Lookup PROMOTIONS = new Lookup("/promotions");
    static final Lookup TAXES = new Lookup("/taxes");
    static final Lookup PROVINCES = new Lookup("/regions/provinces");
    static final Lookup CITIES = new Lookup("/regions/cities");

    static final List<Opt> DISCOUNT_TYPE = Opt.pairs("percent", "Percent (%)", "amount", "Amount (Rp)");
    static final List<Opt> ITEM_TYPE = Opt.pairs("product", "Product", "service", "Service");
    static final List<Opt> PAYMENT_TYPE = Opt.pairs("cash", "Paid in full", "credit", "Credit (pay later)");
    static final List<Opt> ROLE = Opt.pairs("administrator", "Administrator", "manager", "Manager", "cashier", "Cashier");
    static final List<Opt> ACTOR_ROLE = Opt.pairs("owner", "Owner", "administrator", "Administrator", "manager", "Manager", "cashier", "Cashier");
    static final List<Opt> GENDER = Opt.pairs("male", "Male", "female", "Female");
    static final List<Opt> WEEKDAYS = Opt.pairs("monday", "Monday", "tuesday", "Tuesday", "wednesday", "Wednesday", "thursday", "Thursday",
            "friday", "Friday", "saturday", "Saturday", "sunday", "Sunday");
    static final List<Opt> PRICE_TYPE = Opt.pairs("single", "Single price", "wholesale", "Wholesale tiers", "customer_type", "Per customer type");
    static final List<Opt> UNIT_BASE = Opt.pairs("kg", "Kilogram (kg)", "ons", "Ons", "g", "Gram (g)", "l", "Liter (L)", "ml", "Milliliter (ml)", "pcs", "Piece (pcs)");
    static final List<Opt> SALES_PAYMENT_METHODS = Opt.pairs("cash", "Cash", "debit", "Debit card", "credit_card", "Credit card", "qris", "QRIS",
            "e_wallet", "E-wallet", "bank_transfer", "Bank transfer", "other", "Other");
    /** Online payment channels for invoices and top-ups. */
    static final List<Opt> PAYMENT_CHANNELS = Opt.pairs("virtual_account", "Virtual account", "qris", "QRIS", "e_wallet", "E-wallet",
            "credit_card", "Credit card", "convenience_store", "Convenience store", "manual_transfer", "Manual bank transfer");
    static final List<Opt> COIN = Opt.pairs("coin", "Premium coin balance");

    static List<Field> list(Field... f) {
        return Arrays.asList(f);
    }

    /** Receipt list used by report row details (date, receipt, cashier, method, total). */
    static final List<Field> RECEIPT_COLUMNS = list(
            Field.col("created_at", "Time", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("cashier_name", "Cashier"),
            Field.col("payment_method", "Method"), Field.col("grand_total", "Total", "money"));
}
