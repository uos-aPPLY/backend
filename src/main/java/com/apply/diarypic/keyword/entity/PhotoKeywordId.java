package com.apply.diarypic.keyword.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PhotoKeywordId implements Serializable {
    private Long diaryPhoto;
    private Long keyword;
}