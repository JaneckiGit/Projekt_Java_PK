module com.stockdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.json;

    exports com.stockdemo;
    exports com.stockdemo.model;
    exports com.stockdemo.service;
    exports com.stockdemo.ui;
}
