COMPILE_DEPS = CORE_DEPS + [
    "@org_apache_servicemix_bundles_snmp4j//jar",
    "@jackson_databind//jar",
    "@javax_ws_rs_api//jar",
    "@jersey_media_sse//jar",
    "//drivers/utilities:onos-drivers-utilities",
    "//protocols/netconf/api:onos-protocols-netconf-api",
    "//protocols/rest/api:onos-protocols-rest-api-native",
    "//apps/optical-model:onos-apps-optical-model",
    "//drivers/odtn-driver:onos-drivers-odtn-driver",
    "//drivers/optical:onos-drivers-optical",
    "//apps/odtn/api:onos-apps-odtn-api-native",
]

BUNDLES = [
    ":onos-drivers-czechlight",
    "//drivers/utilities:onos-drivers-utilities",
    "@jersey_media_sse//jar",
]

osgi_jar_with_tests(
    resources = glob(["src/main/resources/**"]),
    resources_root = "src/main/resources",
    test_deps = TEST_ADAPTERS,
    deps = COMPILE_DEPS,
)

onos_app(
    app_name = "org.onosproject.drivers.czechlight",
    category = "Drivers",
    description = "Device drivers for CzechLight SDN ROADMs",
    included_bundles = BUNDLES,
    required_apps = [
        "org.onosproject.optical-model",
        "org.onosproject.restsb",
        "org.onosproject.netconf",
        "org.onosproject.drivers.odtn-driver",
        "org.onosproject.drivers.netconf",
        "org.onosproject.drivers.optical",
    ],
    title = "CzechLight Drivers",
    url = "https://czechlight.cesnet.cz/en/open-line-system/sdn-roadm",
)
