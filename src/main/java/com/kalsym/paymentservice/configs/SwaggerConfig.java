package com.kalsym.paymentservice.configs;

import com.google.common.collect.Lists;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.List;

/**
 * @author Sarosh
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String DEFAULT_INCLUDE_PATTERN = "/.*";
//    @Bean
//    public Docket productApi() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .select()
//                //.paths(PathSelectors.any())
//                .apis(RequestHandlerSelectors.basePackage("com.kalsym.paymentservice"))
//                .build()
//                .apiInfo(apiInfo());
//    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder().title("payment-service API Document")
                .description("Used to make payment via delivery partners")
                .termsOfServiceUrl("not added yet")
                .license("not added yet")
                .licenseUrl("").version("2.1.2").build();
    }


    @Bean
    public Docket paymentServiceApi() {

        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .pathMapping("/")
                .apiInfo(ApiInfo.DEFAULT)
                //.forCodeGeneration(true)
                //.genericModelSubstitutes(ResponseEntity.class)
                //.ignoredParameterTypes(Pageable.class)
                //.ignoredParameterTypes(java.sql.Date.class)
                //.directModelSubstitute(java.time.LocalDate.class, java.sql.Date.class)
                //.directModelSubstitute(java.time.ZonedDateTime.class, Date.class)
                //.directModelSubstitute(java.time.LocalDateTime.class, Date.class)
                .securityContexts(Lists.newArrayList(securityContext()))
                .securitySchemes(Lists.newArrayList(new ApiKey("Bearer", AUTHORIZATION_HEADER, "header")))
                .useDefaultResponseMessages(false);
        docket = docket.select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo());
        return docket;

    }

    public SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex(DEFAULT_INCLUDE_PATTERN))
                .build();
    }

    List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope
                = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Lists.newArrayList(
                new SecurityReference("Bearer", authorizationScopes));
    }
}
