package id.lestari.mobile.defs;

import static id.lestari.mobile.defs.Common.ACTOR_ROLE;
import static id.lestari.mobile.defs.Common.CATEGORIES;
import static id.lestari.mobile.defs.Common.CUSTOMERS;
import static id.lestari.mobile.defs.Common.CUSTOMER_TYPES;
import static id.lestari.mobile.defs.Common.DISCOUNT_TYPE;
import static id.lestari.mobile.defs.Common.ITEM_TYPE;
import static id.lestari.mobile.defs.Common.PRICE_TYPE;
import static id.lestari.mobile.defs.Common.PRODUCTS;
import static id.lestari.mobile.defs.Common.STAFF;
import static id.lestari.mobile.defs.Common.TAXES;
import static id.lestari.mobile.defs.Common.UNIT_BASE;
import static id.lestari.mobile.defs.Common.WEEKDAYS;
import static id.lestari.mobile.defs.Common.list;

import id.lestari.mobile.core.Action;
import id.lestari.mobile.core.Field;
import id.lestari.mobile.core.I18n;
import id.lestari.mobile.core.Json;
import id.lestari.mobile.core.Opt;
import id.lestari.mobile.core.Page;
import id.lestari.mobile.core.Registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Database group: products, stock, categories, customers, suppliers, discounts … stock opname (js/modules/database.js). */
final class DatabaseDefs {
    private DatabaseDefs() {}

    private static Map<String, Object> perPage100() {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("per_page", 100);
        return q;
    }

    static void register(Registry r) {
        r.add(new Page(Page.Kind.CRUD, "products", "Products & Services").singular("Product").endpoint("/products")
                .desc("Items you sell, with prices, stock, units, variants and wholesale tiers.")
                .ops("list", "create", "read", "update", "delete", "bulk_delete", "import", "export")
                .importModes(Opt.pairs("create", "Add new products", "update", "Update existing products (matched by SKU)", "product_types", "Product types (units, variants, wholesale)"))
                .search("Search name, SKU or barcode")
                .filters(Field.lookup("category_id", "Category", CATEGORIES), Field.select("item_type", "Type", ITEM_TYPE),
                        Field.select("stock_status", "Stock", Opt.pairs("in_stock", "In stock", "low", "Low stock", "out", "Out of stock")))
                .columns(Field.image("image_url", "").noSort(), Field.col("name", "Name"), Field.col("sku", "SKU"), Field.col("category.name", "Category"),
                        Field.col("selling_price", "Selling price", "money"), Field.col("purchase_price", "Cost", "money"),
                        Field.col("stock", "Stock", "number"), Field.col("updated_at", "Updated", "datetime"))
                .fields(
                        Field.heading("Basic information"),
                        Field.image("image_url", "Photo").w(12),
                        Field.text("name", "Name").req(),
                        Field.select("item_type", "Type", ITEM_TYPE).def("product").req(),
                        Field.text("sku", "SKU / code").help("Leave blank to generate one automatically."),
                        Field.text("barcode", "Barcode"),
                        Field.lookup("category_id", "Category", CATEGORIES),
                        Field.text("unit", "Unit").ph("pcs, box, kg…"),
                        Field.heading("Pricing"),
                        Field.money("selling_price", "Selling price").req().min(0),
                        Field.money("purchase_price", "Purchase price (cost)").min(0),
                        Field.select("price_type", "Price type", PRICE_TYPE).def("single"),
                        Field.lookup("tax_id", "Tax", TAXES),
                        Field.select("discount_type", "Discount type", DISCOUNT_TYPE),
                        Field.number("discount_value", "Discount value").min(0),
                        Field.items("wholesale_prices", "Wholesale tiers", Field.number("min_quantity", "Min. qty"), Field.money("price", "Price"))
                                .showIf("price_type", "wholesale"),
                        Field.items("customer_type_prices", "Price per customer type",
                                Field.lookup("customer_type_id", "Customer type", CUSTOMER_TYPES), Field.money("price", "Price"))
                                .showIf("price_type", "customer_type"),
                        Field.heading("Stock"),
                        Field.sw("track_stock", "Track stock").def(true).noList(),
                        Field.number("stock", "Current stock").help("Only used when creating; adjust later from Stock Management."),
                        Field.number("minimum_stock", "Low-stock alert at").min(0),
                        Field.text("shelf_location", "Shelf location"),
                        Field.number("weight_grams", "Weight (grams)").min(0),
                        Field.sw("track_imei", "Track IMEI / serial numbers").noList(),
                        Field.items("units", "Multiple units", Field.text("name", "Unit"), Field.number("conversion", "Qty of base unit"),
                                Field.money("selling_price", "Price"), Field.text("barcode", "Barcode")).help("E.g. sell by box of 12 and by piece."),
                        Field.items("variants", "Variants", Field.text("name", "Variant"), Field.text("sku", "SKU"), Field.money("selling_price", "Price"),
                                Field.money("purchase_price", "Cost"), Field.number("stock", "Stock")),
                        Field.items("ingredients", "Recipe / raw materials", Field.lookup("product_id", "Raw material", PRODUCTS),
                                Field.number("quantity", "Qty"), Field.select("unit", "Unit", UNIT_BASE))
                                .help("Stock of these products is reduced when this item is sold."),
                        Field.heading("Loyalty & online store"),
                        Field.number("points_earned", "Points earned per purchase").min(0),
                        Field.number("points_to_redeem", "Points needed to redeem").min(0),
                        Field.sw("show_in_online_store", "Show in online store").def(true),
                        Field.textarea("description", "Notes"),
                        Field.textarea("online_description", "Online store description"))
                .toolbar(
                        Action.download("barcodes", "Export barcodes", "/products/barcodes", "barcodes.pdf")
                                .form(Field.multiLookup("product_ids", "Products (empty = all)", PRODUCTS).w(12),
                                        Field.select("symbology", "Barcode type", Opt.pairs("code128", "Code 128", "ean13", "EAN-13", "ean8", "EAN-8", "qr", "QR code")).def("code128"),
                                        Field.number("copies", "Copies per product").def(1).min(1),
                                        Field.select("paper_size", "Paper", Opt.of("a4", "label_33x15", "label_50x25")).def("a4"),
                                        Field.sw("show_price", "Print price").def(true)),
                        Action.download("labels", "Export label maker", "/products/labels", "labels.xlsx")
                                .form(Field.multiLookup("product_ids", "Products (empty = all)", PRODUCTS).w(12)))
                .rowActions(Action.call("duplicate", "Duplicate", "POST", "/products/{id}/duplicate").success("Product duplicated")));

        r.add(new Page(Page.Kind.CRUD, "stock", "Stock Management").singular("Stock").endpoint("/inventory/stocks")
                .desc("Current stock of every tracked product. Adjust counts and view movement history.")
                .idKey("product_id").titleKey("name").ops("list", "import", "export").search("Search product")
                .filters(Field.lookup("category_id", "Category", CATEGORIES), Field.select("stock_status", "Stock", Opt.pairs("low", "Low stock", "out", "Out of stock")))
                .columns(Field.image("image_url", "").noSort(), Field.col("name", "Product"), Field.col("sku", "SKU"), Field.col("category_name", "Category"),
                        Field.col("stock", "Stock", "number"), Field.col("unit", "Unit"), Field.col("minimum_stock", "Min.", "number"),
                        Field.col("stock_value", "Stock value", "money"))
                .fields(Field.text("product_id", "Product ID").ro(), Field.text("name", "Product"), Field.text("sku", "SKU"),
                        Field.number("stock", "Stock"), Field.text("unit", "Unit"), Field.number("minimum_stock", "Minimum"),
                        Field.money("stock_value", "Stock value"), Field.datetime("updated_at", "Updated"))
                .toolbar(Action.call("recalculate", "Recalculate stock", "POST", "/inventory/stocks/recalculate")
                        .confirm("Rebuild stock levels from the transaction history? This can take a while for large catalogs.")
                        .success("Stock recalculation started"))
                .rowActions(
                        Action.call("adjust", "Adjust stock", "POST", "/inventory/stocks/{product_id}/adjustments").success("Stock updated")
                                .values((host, row) -> Json.obj("quantity", row.opt("stock")))
                                .form(Field.select("mode", "Adjustment", Opt.pairs("set", "Set to exact amount", "add", "Add", "subtract", "Subtract")).req().def("set"),
                                        Field.number("quantity", "Quantity").req(),
                                        Field.money("unit_cost", "Unit cost (for added stock)").showIf("mode", "add"),
                                        Field.select("reason", "Reason", Opt.of("correction", "damaged", "expired", "lost", "received", "other")),
                                        Field.textarea("note", "Note")),
                        Action.custom("history", "Movement history", (host, row, ctx) -> host.table(I18n.t("Stock history") + " · " + row.optString("name"),
                                "/inventory/stocks/" + row.optString("product_id") + "/movements", perPage100(),
                                list(Field.col("created_at", "Date", "datetime"), Field.col("type", "Type", "badge"), Field.col("reference", "Reference"),
                                        Field.col("quantity_change", "Change", "number"), Field.col("stock_after", "Stock after", "number"), Field.col("actor_name", "By")),
                                "No movements yet"))));

        r.add(new Page(Page.Kind.CRUD, "categories", "Categories").singular("Category").endpoint("/categories")
                .ops("list", "create", "read", "update", "delete", "bulk_delete").search("Search category")
                .fields(Field.text("name", "Name").req(), Field.color("color", "Color").def("#0f9d76"), Field.number("sort_order", "Sort order"),
                        Field.number("product_count", "Products").noForm(), Field.textarea("description", "Description")));

        List<Field> customerFields = list(
                Field.text("name", "Full name").req(), Field.tel("phone", "Phone"), Field.email("email", "Email"),
                Field.text("code", "Customer code").help("Used for barcode/QR lookup at the cashier."),
                Field.lookup("customer_type_id", "Customer type", CUSTOMER_TYPES), Field.date("birth_date", "Birth date").noList(),
                Field.number("points", "Points").noForm(), Field.money("receivable_balance", "Outstanding credit").noForm(),
                Field.textarea("address", "Address"), Field.textarea("note", "Note").noList());
        r.add(new Page(Page.Kind.CRUD, "customers", "Customers").singular("Customer").endpoint("/customers")
                .ops("list", "create", "read", "update", "delete", "bulk_delete", "import", "export")
                .search("Search name, phone, email or code")
                .filters(Field.lookup("customer_type_id", "Customer type", CUSTOMER_TYPES))
                .columns(Field.col("name", "Name"), Field.col("email", "Email"), Field.col("phone", "Phone"), Field.col("address", "Address"),
                        Field.col("points", "Points", "number"), Field.col("code", "Code"), Field.col("customer_type.name", "Customer type"))
                .fields(customerFields)
                .toolbar(
                        Action.download("qr", "Export QR codes", "/customers/qr-codes", "customer-qr-codes.pdf")
                                .form(Field.multiLookup("customer_ids", "Customers (empty = all)", CUSTOMERS).w(12),
                                        Field.select("layout", "Layout", Opt.of("a4_grid", "card")).def("a4_grid")),
                        Action.call("import_points", "Import points", "POST", "/customers/points/import").upload().success("Points imported")
                                .form(Field.file("file", "Spreadsheet (.xlsx)").accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv").req().w(12),
                                        Field.select("mode", "Mode", Opt.pairs("add", "Add to current points", "set", "Replace current points")).def("add")))
                .rowActions(
                        Action.call("points", "Adjust points", "POST", "/customers/{id}/points").success("Points updated")
                                .form(Field.select("mode", "Mode", Opt.pairs("add", "Add", "subtract", "Subtract")).req().def("add"),
                                        Field.number("points", "Points").req().min(1), Field.textarea("note", "Note")),
                        Action.custom("point_history", "Point history", (host, row, ctx) -> host.table(I18n.t("Point history") + " · " + row.optString("name"),
                                "/customers/" + row.optString("id") + "/point-history", perPage100(),
                                list(Field.col("created_at", "Date", "datetime"), Field.col("type", "Type", "badge"), Field.col("points", "Points", "number"),
                                        Field.col("balance_after", "Balance", "number"), Field.col("reference", "Reference"), Field.col("note", "Note")),
                                "No point history"))));

        r.add(new Page(Page.Kind.CRUD, "customer-types", "Customer Types").singular("Customer type").endpoint("/customer-types").plugin("business")
                .desc("Group customers (e.g. Member, Reseller) to give them special prices or discounts.")
                .ops("list", "create", "read", "update", "delete")
                .fields(Field.text("name", "Name").req(), Field.select("discount_type", "Automatic discount type", DISCOUNT_TYPE),
                        Field.number("discount_value", "Discount value").min(0), Field.number("point_multiplier", "Point multiplier").def(1).min(0),
                        Field.number("customer_count", "Customers").noForm(), Field.textarea("description", "Description")));

        r.add(new Page(Page.Kind.CRUD, "suppliers", "Suppliers").singular("Supplier").endpoint("/suppliers")
                .ops("list", "create", "read", "update", "delete", "bulk_delete", "import", "export").search("Search supplier")
                .fields(Field.text("name", "Name").req(), Field.text("contact_person", "Contact person"), Field.tel("phone", "Phone"),
                        Field.email("email", "Email"), Field.text("code", "Supplier code"), Field.money("payable_balance", "Outstanding debt").noForm(),
                        Field.textarea("address", "Address")));

        r.add(new Page(Page.Kind.CRUD, "discounts", "Discounts").singular("Discount").endpoint("/discounts")
                .ops("list", "create", "read", "update", "delete", "bulk_delete")
                .fields(Field.text("name", "Name").req(), Field.select("type", "Discount type", DISCOUNT_TYPE).req().def("percent"),
                        Field.number("value", "Value").req().min(0), Field.sw("is_active", "Active").def(true)));

        r.add(new Page(Page.Kind.CRUD, "taxes", "Taxes").singular("Tax").endpoint("/taxes")
                .ops("list", "create", "read", "update", "delete", "bulk_delete")
                .fields(Field.text("name", "Name").req(), Field.percent("rate", "Rate").req().min(0).max(100),
                        Field.sw("is_inclusive", "Price already includes this tax"), Field.sw("is_active", "Active").def(true)));

        r.add(new Page(Page.Kind.CRUD, "fees", "Fee Module").singular("Fee").endpoint("/fees")
                .desc("Extra charges the cashier can add to a sale or purchase (packaging, delivery, etc.).")
                .ops("list", "create", "read", "update", "delete")
                .fields(Field.text("name", "Name").req(), Field.select("amount_type", "Type", DISCOUNT_TYPE).def("amount").req(),
                        Field.number("amount", "Amount").req().min(0),
                        Field.select("applies_to", "Applies to", Opt.pairs("sale", "Sales", "purchase", "Purchases", "both", "Both")).def("sale")));

        r.add(new Page(Page.Kind.CRUD, "cash-drawers", "Cash Drawers").singular("Cash drawer").endpoint("/cash-drawers")
                .ops("list", "create", "read", "update", "delete")
                .fields(Field.text("name", "Name").req(), Field.sw("is_active", "Active").def(true),
                        Field.text("current_shift_status", "Shift").noForm()));

        List<Opt> promoTypes = Opt.pairs("product_discount", "Product discount", "buy_x_get_y", "Buy X get Y", "minimum_purchase", "Minimum purchase discount");
        r.add(new Page(Page.Kind.CRUD, "promotions", "Promotions").singular("Promotion").endpoint("/promotions").plugin("business")
                .desc("Time-limited deals applied automatically at checkout.")
                .ops("list", "create", "read", "update", "delete")
                .tabs("status", "", Opt.pairs("", "All", "active", "Active", "scheduled", "Scheduled", "ended", "Ended"))
                .columns(Field.col("name", "Name"), Field.select("type", "Type", promoTypes), Field.col("start_date", "Starts", "date"),
                        Field.col("end_date", "Ends", "date"), Field.col("is_active", "Active", "boolean"))
                .fields(Field.text("name", "Name").req(), Field.select("type", "Promotion type", promoTypes).req().def("product_discount"),
                        Field.date("start_date", "Start date").req(), Field.date("end_date", "End date").req(),
                        Field.time("start_time", "Daily start time"), Field.time("end_time", "Daily end time"),
                        Field.multi("days", "Active days", WEEKDAYS), Field.multiLookup("customer_type_ids", "Only for customer types", CUSTOMER_TYPES),
                        Field.select("discount_type", "Discount type", DISCOUNT_TYPE).showIf("type", "product_discount", "minimum_purchase"),
                        Field.number("discount_value", "Discount value").min(0).showIf("type", "product_discount", "minimum_purchase"),
                        Field.money("minimum_purchase", "Minimum purchase").showIf("type", "minimum_purchase"),
                        Field.number("buy_quantity", "Buy quantity (X)").showIf("type", "buy_x_get_y"),
                        Field.number("get_quantity", "Free quantity (Y)").showIf("type", "buy_x_get_y"),
                        Field.items("products", "Products", Field.lookup("product_id", "Product", PRODUCTS),
                                Field.lookup("free_product_id", "Free product (Buy X get Y)", PRODUCTS)).showIf("type", "product_discount", "buy_x_get_y"),
                        Field.sw("is_active", "Active").def(true), Field.textarea("description", "Description")));

        r.add(new Page(Page.Kind.SETTINGS, "points", "Loyalty Points").endpoint("/settings/points").plugin("business")
                .desc("How customers earn and redeem points.")
                .fields(Field.sw("is_enabled", "Enable loyalty points").w(12), Field.money("earn_spend_amount", "For every purchase of").min(0),
                        Field.number("earn_points", "Customer earns (points)").min(0), Field.money("redeem_point_value", "Value of 1 point when redeemed").min(0),
                        Field.number("minimum_redeem_points", "Minimum points to redeem").min(0),
                        Field.number("expiry_days", "Points expire after (days, 0 = never)").min(0),
                        Field.sw("earn_on_credit_sales", "Earn points on unpaid (credit) sales")));

        r.add(new Page(Page.Kind.CRUD, "commissions", "Staff Commissions").singular("Commission rule").endpoint("/commissions").plugin("business")
                .ops("list", "create", "read", "update", "delete")
                .fields(Field.text("name", "Name").req(),
                        Field.select("basis", "Calculated per", Opt.pairs("product", "Product sold", "transaction", "Transaction")).req().def("product"),
                        Field.select("type", "Commission type", DISCOUNT_TYPE).req().def("percent"), Field.number("value", "Value").req().min(0),
                        Field.multiLookup("staff_ids", "Staff", STAFF),
                        Field.multiLookup("product_ids", "Products (empty = all)", PRODUCTS).showIf("basis", "product"),
                        Field.sw("is_active", "Active").def(true)));

        r.add(new Page(Page.Kind.CRUD, "stock-opnames", "Stock Opname").singular("Stock opname").endpoint("/stock-opnames")
                .desc("Physical stock counts. Approving an opname sets stock to the counted quantity.")
                .ops("list", "create", "read", "delete", "bulk_delete", "import").titleKey("code")
                .columns(Field.col("code", "Number"), Field.col("opname_date", "Date", "date"), Field.col("performed_by_name", "Counted by"),
                        Field.col("item_count", "Items", "number"), Field.col("difference_value", "Difference value", "money"), Field.col("status", "Status", "badge"))
                .fields(Field.date("opname_date", "Date").req(), Field.text("performed_by_name", "Counted by").req(),
                        Field.select("performed_by_role", "Role", ACTOR_ROLE).req(),
                        Field.items("items", "Counted items", Field.lookup("product_id", "Product", PRODUCTS), Field.number("physical_stock", "Counted qty"),
                                Field.text("note", "Note")),
                        Field.textarea("note", "Note"))
                .detailFields(Field.col("code", "Number"), Field.date("opname_date", "Date"), Field.col("performed_by_name", "Counted by"), Field.col("status", "Status"),
                        Field.items("items", "Items", Field.col("product_name", "Product"), Field.col("system_stock", "System", "number"),
                                Field.col("physical_stock", "Counted", "number"), Field.col("difference", "Difference", "number"), Field.col("note", "Note")),
                        Field.col("note", "Note"))
                .rowActions(
                        Action.call("approve", "Approve & apply", "POST", "/stock-opnames/{id}/approve").confirm("Apply counted quantities to stock?")
                                .success("Stock opname applied").when(row -> "pending".equals(row.optString("status"))),
                        Action.call("reject", "Reject", "POST", "/stock-opnames/{id}/reject").danger().success("Stock opname rejected")
                                .form(Field.textarea("reason", "Reason")).when(row -> "pending".equals(row.optString("status"))),
                        Action.download("download", "Download", "/stock-opnames/{id}/export", "stock-opname.xlsx")));
    }
}
