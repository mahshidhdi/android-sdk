package io.hengam.lib.analytics.event;

public class Ecommerce {

    private String name;
    private Double price;
    private String category;
    private Long quantity;

    private Ecommerce(String name, Double price, String category, Long quantity) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.quantity = quantity;
    }

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public Long getQuantity() {
        return quantity;
    }

    public static class Builder {
        private String name;
        private Double price;
        private String category = null;
        private Long quantity = null;

        public Builder(String name, Double price) {
            this.name = name;
            this.price = price;
        }

        public Builder setCategory(String category) {
            this.category = category;
            return this;
        }

        public Builder setQuantity(Long quantity) {
            this.quantity = quantity;
            return this;
        }

        public Ecommerce build() {
            return new Ecommerce(name, price, category, quantity);
        }
    }
}
