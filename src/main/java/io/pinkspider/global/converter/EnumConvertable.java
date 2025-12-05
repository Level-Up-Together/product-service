package io.pinkspider.global.converter;

/*
 * enum class에 대해서 generic하게 컨버터를 사용하기 위한 인터페이스로
 * enum의 상수값을 가져온다.
 * created by basquiat
 */
public interface EnumConvertable<T> {

    String value();

    T ofNull();
}
