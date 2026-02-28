package com.tradenova.common.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// JPA에게 '이건 에티티 필드 변환기야' 라고 알려주는 어노테이션
@Converter(autoApply = false)
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    // JSON <-> String 반환용 Jackson 객체
    private static final ObjectMapper om = new ObjectMapper();

    /**
     * 엔티티 -> DB 저장 시 호출됨
     *
     * JsonNode 객체를 DB에 저장 가능한 String(JSON 문자열)로 변환
     */
    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        try{
            // null이면 그냥 null 저장
            // 아니면 JSON 객체를 문자열로 직렬화
            return attribute == null ? null : om.writeValueAsString(attribute);
        } catch (Exception e){
            // JSON 변환 실패 시 예외 발생
            throw new IllegalArgumentException("JSON serialize failed", e);
        }
    }

    /**
     * DB -> 엔티티 로딩 시 호출됨
     *
     * DB에 저장된 JSON 문자열을 JsonNode 객체로 변환
     */
    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        try {
            // null이면 null 반환
            // 아니면 문자열을 JSON 트리 객체로 파싱
            return dbData == null ? null : om.readTree(dbData);
        } catch (Exception e) {
            // JSON 파싱 실패 시 예외 발생
            throw new IllegalArgumentException("JSON parse failed", e);
        }
    }

}
