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

    static List<Opt> DISCOUNT_TYPE;
    static List<Opt> ITEM_TYPE;
    static List<Opt> PAYMENT_TYPE;
    static List<Opt> ROLE;
    static List<Opt> ACTOR_ROLE;
    static List<Opt> GENDER;
    static List<Opt> WEEKDAYS;
    static List<Opt> PRICE_TYPE;
    static List<Opt> UNIT_BASE;
    static List<Opt> SALES_PAYMENT_METHODS;
    /** Online payment channels for invoices and top-ups. */
    static List<Opt> PAYMENT_CHANNELS;
    static List<Opt> COIN;

    /** (Re)builds the shared options and fields in the current language; called by Defs.build(). */
    static void init() {
        DISCOUNT_TYPE = Opt.pairs("percent", "Percent (%)", "amount", "Amount (Rp)");
        ITEM_TYPE = Opt.pairs("product", "Product", "service", "Service");
        PAYMENT_TYPE = Opt.pairs("cash", "Paid in full", "credit", "Credit (pay later)");
        ROLE = Opt.pairs("administrator", "Administrator", "manager", "Manager", "cashier", "Cashier");
        ACTOR_ROLE = Opt.pairs("owner", "Owner", "administrator", "Administrator", "manager", "Manager", "cashier", "Cashier");
        GENDER = Opt.pairs("male", "Male", "female", "Female");
        WEEKDAYS = Opt.pairs("monday", "Monday", "tuesday", "Tuesday", "wednesday", "Wednesday", "thursday", "Thursday",
                "friday", "Friday", "saturday", "Saturday", "sunday", "Sunday");
        PRICE_TYPE = Opt.pairs("single", "Single price", "wholesale", "Wholesale tiers", "customer_type", "Per customer type");
        UNIT_BASE = Opt.pairs("kg", "Kilogram (kg)", "ons", "Ons", "g", "Gram (g)", "l", "Liter (L)", "ml", "Milliliter (ml)", "pcs", "Piece (pcs)");
        SALES_PAYMENT_METHODS = Opt.pairs("cash", "Cash", "debit", "Debit card", "credit_card", "Credit card", "qris", "QRIS",
                "e_wallet", "E-wallet", "bank_transfer", "Bank transfer", "other", "Other");
        PAYMENT_CHANNELS = Opt.pairs("virtual_account", "Virtual account", "qris", "QRIS", "e_wallet", "E-wallet",
                "credit_card", "Credit card", "convenience_store", "Convenience store", "manual_transfer", "Manual bank transfer");
        COIN = Opt.pairs("coin", "Premium coin balance");
        RECEIPT_COLUMNS = list(
                Field.col("created_at", "Time", "datetime"), Field.col("receipt_number", "Receipt"), Field.col("cashier_name", "Cashier"),
                Field.col("payment_method", "Method"), Field.col("grand_total", "Total", "money"));
    }

    static List<Field> list(Field... f) {
        return Arrays.asList(f);
    }

    /** Receipt list used by report row details (date, receipt, cashier, method, total). */
    static List<Field> RECEIPT_COLUMNS;
}
