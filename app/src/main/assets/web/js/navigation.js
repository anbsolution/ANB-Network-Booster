/* ANB Network Booster - Screen Navigation */

(function () {
    "use strict";

    const VALID_SCREENS = [
        "home",
        "signal",
        "speed",
        "history",
        "guide",
        "settings"
    ];

    let currentScreen = "home";

    function getScreen(name) {
        return document.getElementById("screen-" + name);
    }

    function setActiveNavigation(name) {
        document.querySelectorAll(
            ".bottom-navigation [data-target]"
        ).forEach(function (button) {
            const active = button.dataset.target === name;

            if (active) {
                button.setAttribute("aria-current", "page");
            } else {
                button.removeAttribute("aria-current");
            }
        });
    }

    function showScreen(name) {
        if (!VALID_SCREENS.includes(name)) {
            return false;
        }

        const target = getScreen(name);

        if (!target) {
            return false;
        }

        document.querySelectorAll(".app-screen").forEach(
            function (screen) {
                screen.hidden = screen !== target;
            }
        );

        currentScreen = name;
        setActiveNavigation(name);

        window.dispatchEvent(
            new CustomEvent("anb:screenchange", {
                detail: { screen: name }
            })
        );

        return true;
    }

    function initializeNavigation() {
        document.querySelectorAll(
            ".bottom-navigation [data-target]"
        ).forEach(function (button) {
            button.addEventListener("click", function () {
                showScreen(button.dataset.target);
            });
        });

        showScreen("home");
    }

    window.ANBNavigation = Object.freeze({
        initialize: initializeNavigation,
        show: showScreen,
        getCurrentScreen: function () {
            return currentScreen;
        },
        getAvailableScreens: function () {
            return VALID_SCREENS.slice();
        }
    });

})();
