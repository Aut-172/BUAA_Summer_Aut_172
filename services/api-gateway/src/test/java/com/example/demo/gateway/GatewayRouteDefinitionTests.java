package com.example.demo.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewayRouteDefinitionTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void authRoutesAreSplitByOwnedService() {
        Map<String, RouteDefinition> routes = loadRoutesById();

        assertRoute(routes, "user-auth", "lb://user-service", "/api/auth/login", "/api/auth/register", "/api/auth/admin/**");
        assertRoute(routes, "merchant-auth", "lb://merchant-service", "/api/auth/merchant/**");
        assertRoute(routes, "rider-auth", "lb://fulfillment-service", "/api/auth/rider/**");
    }

    @Test
    void userReviewRouteHasPriorityOverUserServiceFallback() {
        Map<String, RouteDefinition> routes = loadRoutesById();

        RouteDefinition reviewRoute = routes.get("engagement-user-reviews");
        RouteDefinition userRoute = routes.get("user-service");

        assertThat(reviewRoute).isNotNull();
        assertThat(reviewRoute.getUri()).hasToString("lb://engagement-service");
        assertThat(reviewRoute.getOrder()).isLessThan(userRoute.getOrder());
        assertThat(pathPatterns(reviewRoute)).contains("/api/user/reviews");
        assertThat(pathPatterns(userRoute)).contains("/api/user/**");
    }

    @Test
    void roleAndDomainRoutesTargetExpectedServices() {
        Map<String, RouteDefinition> routes = loadRoutesById();

        assertRoute(routes, "fulfillment-service", "lb://fulfillment-service", "/api/rider/**", "/api/delivery/**", "/api/admin/riders/**");
        assertRoute(routes, "engagement-service", "lb://engagement-service", "/api/reviews/**", "/api/messages/**", "/api/uploads/**");
        assertRoute(routes, "admin-order-service", "lb://order-service", "/api/admin/orders", "/api/admin/orders/**");
        assertRoute(routes, "settlement-service", "lb://settlement-service", "/api/coupons/**", "/api/orders/*/pay", "/api/orders/*/payments");
    }

    private Map<String, RouteDefinition> loadRoutesById() {
        return routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .block()
                .stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    private void assertRoute(Map<String, RouteDefinition> routes, String id, String uri, String... paths) {
        RouteDefinition route = routes.get(id);

        assertThat(route).as("route %s", id).isNotNull();
        assertThat(route.getUri()).hasToString(uri);
        assertThat(pathPatterns(route)).contains(paths);
    }

    private String pathPatterns(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .collect(Collectors.joining(","));
    }
}
