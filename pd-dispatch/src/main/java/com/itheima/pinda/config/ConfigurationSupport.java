package com.itheima.pinda.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.itheima.pinda.common.converter.EnumDeserializer;
import com.itheima.pinda.common.json.BigDecimalSerializer;
import com.itheima.pinda.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@Slf4j
@Configuration
@EnableSwagger2
public class ConfigurationSupport extends WebMvcConfigurationSupport {
    /**
     * deserializerByType ??????string?????????????????? LocalDateTime ????????????
     *
     * @return
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> builder
                .deserializerByType(Enum.class, EnumDeserializer.INSTANCE)
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_TIME_FORMAT)))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_FORMAT)))
                .deserializerByType(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_TIME_FORMAT)));

    }

    @Bean
    public Docket createRestApi() {
        // ????????????
        return new Docket(DocumentationType.SWAGGER_2)
                // ??????api???????????????
                .apiInfo(apiInfo())
                // ???????????????????????????
                .select()
                // ????????????
                .apis(RequestHandlerSelectors.basePackage("com.itheima.pinda.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("??????????????????????????????--Swagger??????")
                .version("1.0")
                .build();
    }

    /**
     * ??????@EnableMvc???????????????????????????????????????????????????????????????
     *
     * @param registry
     */
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ??????????????????????????????
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        // ??????swagger????????????
        registry.addResourceHandler("/swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        // ??????swagger???js??????????????????
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");

    }

    /**
     * ???????????????null??????
     * ???????????? ''
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("?????????jackson??????( null -> ''?????????????????????????????? )");
        //???json?????????????????????string??????
        converters.add(new StringHttpMessageConverter());
        //??????json??????
        MappingJackson2HttpMessageConverter jackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
        jackson2HttpMessageConverter.setObjectMapper(new JacksonObjectMapper());
        converters.add(jackson2HttpMessageConverter);
        //?????????????????????
        super.addDefaultHttpMessageConverters(converters);
    }

    class JacksonObjectMapper extends ObjectMapper {
        public JacksonObjectMapper() {
            super();
            //?????????????????????????????????
            this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

            //????????????????????????????????????????????????
            this.getDeserializationConfig().withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            SimpleModule simpleModule = new SimpleModule()
//                .addDeserializer(Enum.class, EnumDeserializer.INSTANCE)
//                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_TIME_FORMAT)))
//                .addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_FORMAT)))
//                .addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_TIME_FORMAT)))

                    .addSerializer(BigInteger.class, ToStringSerializer.instance)
                    .addSerializer(BigDecimal.class, new BigDecimalSerializer())
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_TIME_FORMAT)))
                    .addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_FORMAT)))
                    .addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(DateUtils.DEFAULT_TIME_FORMAT)))
                    .addSerializer(Date.class, new DateSerializer(false, new SimpleDateFormat(DateUtils.DEFAULT_DATE_TIME_FORMAT)));

            this.registerModule(simpleModule);
            //??????????????????????????????
            this.getSerializerProvider().setNullValueSerializer(new JsonSerializer<Object>() {
                @Override
                public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    String fieldName = gen.getOutputContext().getCurrentName();
                    try {
                        //????????????????????????
                        Field field = gen.getCurrentValue().getClass().getDeclaredField(fieldName);
                        if (Objects.equals(field.getType(), String.class)) {
                            //??????????????????""
                            gen.writeString("");
                            return;
                        } else if (Objects.equals(field.getType(), List.class)) {
                            //?????????????????????[]
                            gen.writeStartArray();
                            gen.writeEndArray();
                            return;
                        } else if (Objects.equals(field.getType(), Map.class)) {
                            //map???????????????{}
                            gen.writeStartObject();
                            gen.writeEndObject();
                            return;
                        }
                    } catch (NoSuchFieldException e) {
                    }
                    //????????????""
                    gen.writeString("");
                }
            });
        }
    }
}
