module ttt.fx {
    requires javafx.controls;
    requires javafx.graphics;
    requires org.jdom2;

    opens ttt.controller to javafx.graphics;
    exports ttt.model;
    exports ttt.ai;
    exports ttt.storage;
    exports ttt.util;
    exports ttt.network;
    exports ttt.controller;
    exports ttt.view;
}
