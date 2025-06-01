package com.apply.diarypic.global.config;

import com.apply.diarypic.terms.entity.Terms;
import com.apply.diarypic.terms.entity.TermsType;
import com.apply.diarypic.terms.repository.TermsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TermsRepository termsRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("초기 데이터 적재 시작...");
        initializeTerms();
        log.info("초기 데이터 적재 완료.");
    }

    private void initializeTerms() {
        log.info("약관 데이터 초기화 중...");

        // 1. 만 14세 이상 확인
        String ageConfirmationContent = """
                <head>
                    <meta charset="UTF-8">
                    <title>만 14세 이상 확인</title>
                </head>
                <body>
                    <h1>[필수] 만 14세 이상 확인</h1>
                    <p>본인은 만 14세 이상임을 확인합니다.</p>
                    <p>※ DiaryPic 서비스는 만 14세 이상만 이용할 수 있습니다.</p>
                    <p>만 14세 미만 아동이 회원가입을 하는 경우, 회원탈퇴 또는 서비스 이용이 제한될 수 있음을 알려드립니다.</p>
                </body>
                """;
        createTermIfNotExists(TermsType.AGE_CONFIRMATION, 1, TermsType.AGE_CONFIRMATION.getDescription(), ageConfirmationContent, true, TermsType.AGE_CONFIRMATION.getDisplayOrder());

        // 2. 서비스 이용약관
        String serviceTermsContent = """
                <head>
                    <meta charset="UTF-8">
                    <title>서비스 이용약관</title>
                </head>
                <body>
                    <h1>[필수] 서비스이용약관</h1>
                    <h2>제1장 총칙</h2>
                    <h3>제1조 (목적)</h3>
                    <p>본 약관은 서울시립대학교 컴퓨터과학과 aPPLY 프로젝트팀(이하 “개발자”)이 제공하는 DiaryPic 서비스(이하 “서비스”)의 이용과 관련하여, 개발자와 회원 간의 권리와 의무, 책임, 서비스 이용 조건 및 절차 등 기본적인 사항을 규정함을 목적으로 합니다.</p>
                    <p>서비스는 유료 앱으로, 사용자는 본 약관과 개인정보처리방침을 충분히 숙지한 후 동의하지 않을 경우 앱 설치 및 사용을 중단해야 합니다.</p>
                    <h3>제2조 (용어 정의)</h3>
                    <ul>
                        <li>"서비스"란 DiaryPic 앱 내에서 제공하는 일기 작성, 사진 기반 AI 추천, 감정 태깅, 캘린더 관리 등의 기능을 말합니다.</li>
                        <li>"회원"이란 본 약관에 동의하고 개발자가 제공하는 서비스를 이용하는 자를 의미합니다.</li>
                        <li>"AI 추천"이란 사용자의 사진, 감정 입력, 말투 설정 등 데이터를 기반으로 OpenAI와 같은 외부 인공지능 API를 통해 일기 초안을 생성하는 것을 말합니다.</li>
                        <li>"유료 서비스"란 앱 다운로드 또는 기능 이용 시 일정 요금을 지불해야 하는 서비스를 의미하며, 해당 조건은 앱스토어(Play Store 또는 App Store)의 정책을 따릅니다.</li>
                    </ul>
                    <h3>제3조 (약관의 효력 및 변경)</h3>
                    <p>본 약관은 서비스 화면에 게시하거나 기타 방법으로 공지함으로써 효력을 발생합니다.</p>
                    <p>개발자는 관련 법령을 위배하지 않는 범위 내에서 약관을 변경할 수 있으며, 변경 시 사전 공지합니다.</p>
                    <p>회원은 변경된 약관에 동의하지 않을 경우 서비스 이용을 중단할 수 있습니다.</p>
                    <h3>제4조 (관계 법령의 적용)</h3>
                    <p>본 약관에서 정하지 않은 사항은 「전자상거래법」, 「콘텐츠산업진흥법」, 「개인정보 보호법」 등 관계 법령에 따릅니다.</p>
                    <h2>제2장 서비스 이용</h2>
                    <h3>제5조 (서비스 내용 및 유료화 정책)</h3>
                    <p>서비스는 유료로 제공되며, 사용자는 앱스토어를 통해 결제 후 이용할 수 있습니다.</p>
                    <p>서비스 주요 기능은 다음과 같습니다:</p>
                    <ul>
                        <li>사진 기반 일기 생성</li>
                        <li>AI 감정 분석 및 말투 생성</li>
                        <li>캘린더 기반 일기 관리</li>
                    </ul>
                    <p>유료 서비스 결제 관련 사항은 각 앱마켓의 구매 정책 및 환불 기준을 따릅니다.</p>
                    <h2>제3장 회원가입 및 통정</h2>
                    <h3>제6조 (회원가입)</h3>
                    <p>회원은 가입 과정에서 개발자가 제공하는 정확한 정보를 기입하고, 모든 약관에 동의해야 합니다.</p>
                    <p>DiaryPic 에는 만 14세 이상의 개인만 가입할 수 있으며, 이하의 여부에 대해 개발자는 가입을 거부할 권리가 있습니다.</p>
                    <h3>제7조 (회원 탈퇴)</h3>
                    <p>회원은 서비스 이용을 원하지 않을 경우, 서비스 내에서 제공하는 "탈퇴" 기능을 통해 언제든지 탈퇴할 수 있습니다.</p>
                    <p>탈퇴 시 개인정보는 개인정보처리방침에 따라 즉시 또는 일정 기간 보관 후 안전하게 파기됩니다. 단, 관련 법령에 따라 보관이 필요한 정보는 예외로 합니다.</p>
                    <h2>제4장 개인정보 보호</h2>
                    <h3>제8조 (개인정보의 수집 및 이용)</h3>
                    <p>DiaryPic은 다음과 같은 목적을 위해 최소한의 개인정보를 수집하고 이용합니다.</p>
                    <ul>
                        <li>SNS 로그인 계정 정보(이메일)</li>
                        <li>사용자가 업로드한 사진 및 해당 사진의 메타데이터(촬영 일시, 위치 등)</li>
                        <li>감정 태그, 말투 설정 등 일기 생성에 필요한 사용자 입력 정보</li>
                        <li>AI 추천 결과 생성 및 기능 제공을 위한 전송 정보(IP, 디바이스 정보 등)</li>
                    </ul>
                    <p>개발자는 일기 초안 생성을 위해 필요한 정보를 OpenAI 등 외부 인공지능 API 제공 업체에 전송할 수 있습니다. 이 때 사용자의 사진, 감정 태그, 말투 설정 등이 포함될 수 있으며, 해당 정보는 AI 추천 결과 생성을 위한 용도로만 사용됩니다.</p>
                    <p>위 API 전송 과정에서 발생할 수 있는 예기치 못한 처리 오류나 보안 취약점 등 외부 시스템의 문제로 인한 손해에 대해서는 개발자가 직접적인 통제권이 없으므로 책임을 지지 않습니다.</p>
                    <p>개발자는 개인정보 수집 및 이용과 관련하여 사용자에게 명확히 고지하고, 동의를 얻은 범위 내에서만 처리합니다. 또한, 서비스 제공 목적 외 제3자에게 개인정보를 제공하지 않으며, 사용자의 사전 동의 없이 영리 목적으로 이용하지 않습니다.</p>
                    <p>사용자는 자신의 개인정보에 대해 열람, 수정, 삭제를 요청할 수 있으며, 개발자는 이에 대해 지체 없이 조치합니다.</p>
                    <h2>제5장 저작권 및 콘텐츠 이용</h2>
                    <h3>제9조 (이용자 콘텐츠의 저작권)</h3>
                    <p>회원이 작성한 일기, 사진, 감정 태그 등 모든 콘텐츠에 대한 저작권은 원칙적으로 회원에게 귀속됩니다.</p>
                    <p>단, 회원은 서비스 제공을 위해 필요한 범위 내에서 개발자에게 해당 콘텐츠를 이용할 수 있는 비독점적, 무상, 지역 제한 없는 사용 권한을 부여합니다.</p>
                    <p>개발자는 회원의 콘텐츠를 서비스 개선, AI 학습, 홍보 목적(익명 처리 시)에 한하여 사용할 수 있습니다.</p>
                    <h3>제10조 (서비스 제공자 콘텐츠의 저작권)</h3>
                    <p>서비스 내에서 제공되는 이미지, 텍스트, UI/UX, 소스코드, 알고리즘 등 모든 구성 요소에 대한 저작권은 개발자 또는 정당한 권리를 가진 제3자에게 귀속됩니다.</p>
                    <p>사용자는 개발자의 사전 서면 동의 없이 이를 무단 복제, 배포, 변경, 전시하거나 제3자에게 제공할 수 없습니다.</p>
                    <h2>제6장 유료 서비스 및 환불</h2>
                    <h3>제11조 (유료 서비스 정책)</h3>
                    <p>DiaryPic은 앱 설치 시점부터 유료 서비스로 제공되며, 이용자는 앱마켓(Play Store, App Store 등)의 결제 시스템을 통해 서비스를 구매합니다.</p>
                    <p>유료 결제 금액, 결제 방식, 자동 갱신 여부 등은 각 마켓의 정책에 따르며, 개발자는 이에 개입하지 않습니다.</p>
                    <h3>제12조 (환불 및 해지)</h3>
                    <p>이용자는 결제 후 일정 기간 내에 앱마켓의 환불 정책에 따라 환불을 요청할 수 있습니다.</p>
                    <p>앱 설치 후 기능 이용이 시작된 경우, 사용자는 부분 또는 전액 환불이 제한될 수 있습니다.</p>
                    <p>개발자는 앱마켓이 제공하는 정책 범위 내에서만 환불을 처리하며, 자체 환불은 제공하지 않습니다.</p>
                    <h2>제7장 광고 및 제3자 제공</h2>
                    <h3>제13조 (광고 게재)</h3>
                    <p>개발자는 서비스 운영과 관련하여 앱 내에 배너 광고 또는 제휴 마케팅 콘텐츠를 게재할 수 있습니다.</p>
                    <p>사용자는 광고 수신 동의 여부에 따라 푸시 알림, 이메일 등을 통해 광고 정보를 제공받을 수 있으며, 수신 거부는 언제든지 가능합니다.</p>
                    <h3>제14조 (제3자 정보 제공)</h3>
                    <p>개발자는 이용자의 동의 없이 제3자에게 개인정보를 제공하지 않습니다.</p>
                    <p>다만, 법령에 따른 요청이 있거나 서비스 제공을 위한 기술적 위탁이 필요한 경우, 필요한 범위 내에서 최소한의 정보만을 제공할 수 있으며, 이 경우 별도로 고지하고 동의를 받습니다.</p>
                    <h2>제8장 면책 및 분쟁 해결</h2>
                    <h3>제15조 (면책 조항)</h3>
                    <p>개발자는 다음과 같은 사유로 인한 손해에 대하여 책임을 지지 않습니다:</p>
                    <ul>
                        <li>천재지변, 전쟁, 서비스 장애 등 불가항력적인 사유</li>
                        <li>사용자의 귀책사유로 인한 문제 발생</li>
                        <li>외부 인공지능 API 시스템의 오류 또는 보안 문제</li>
                        <li>이용자가 기대하는 효용을 얻지 못하거나 상실한 경우</li>
                    </ul>
                    <h3>제16조 (분쟁 해결 및 관할)</h3>
                    <p>서비스와 관련하여 분쟁이 발생한 경우, 개발자와 회원은 성실히 협의하여 해결하도록 노력합니다.</p>
                    <p>협의가 이루어지지 않을 경우, 분쟁은 서울시립대학교가 소재한 지역의 관할 법원을 제1심 법원으로 합니다.</p>
                    <p><strong>부칙</strong></p>
                    <p>시행일자: 2025년 6월 1일</p>
                    <p>본 약관은 시행일자부터 효력을 발생합니다.</p>
                </body>
                """;
        createTermIfNotExists(TermsType.SERVICE_TERMS, 1, TermsType.SERVICE_TERMS.getDescription(), serviceTermsContent, true, TermsType.SERVICE_TERMS.getDisplayOrder());

        // 3. 개인정보 처리방침
        String privacyPolicyContent = """
                <head>
                    <meta charset="UTF-8">
                    <title>개인정보 처리방침</title>
                </head>
                <body>
                    <h1>[필수] 개인정보처리방침</h1>
                    <p>📘 <strong>[DiaryPic] 개인정보 처리방침</strong></p>
                    <p>시행일자: 2025.03.01</p>
                    <p>[DiaryPic](이하 ‘회사’라 함)은 「개인정보 보호법」 제30조에 따라 정보주체의 개인정보를 보호하고 이와 관련한 고충을 신속하고 원활하게 처리할 수 있도록 하기 위하여 다음과 같이 개인정보 처리방침을 수립·공개합니다.</p>
                    <h2>1. 개인정보의 처리 목적</h2>
                    <p>회사는 다음의 목적을 위하여 개인정보를 처리합니다. 처리한 개인정보는 다음의 목적 이외의 용도로는 사용되지 않으며, 이용 목적이 변경되는 경우에는 별도 동의를 받는 등 필요한 조치를 이행하겠습니다.</p>
                    <ul>
                        <li><strong>회원 가입 및 관리:</strong> 회원 가입의사 확인, 회원제 서비스 제공에 따른 본인 식별·인증, 회원자격 유지·관리, 서비스 부정이용 방지, 고지사항 전달, 민원처리 등</li>
                        <li><strong>AI 일기 서비스 제공:</strong> 사용자가 업로드한 사진 및 메타데이터를 기반으로 AI 일기 초안을 자동 생성하고, 사용자 맞춤 콘텐츠(말투, 감정 태그 등)를 생성</li>
                        <li><strong>서비스 개선 및 통계 분석:</strong> 기능 향상 및 사용자 편의 개선을 위한 통계 작성 및 분석, 시스템 오류 개선</li>
                        <li><strong>마케팅 및 이벤트 정보 제공(선택 동의 시):</strong> 이벤트 안내, 프로모션 및 광고성 정보 제공 등</li>
                    </ul>
                    <h2>2. 처리하는 개인정보 항목</h2>
                    <ul>
                        <li><strong>회원 가입 시:</strong> 이메일, 이름, 생년월일, 휴대폰 번호</li>
                        <li><strong>(SNS 가입 시):</strong> Google 또는 Apple 계정의 이름, 이메일</li>
                        <li><strong>본인 인증 시:</strong> CI, DI, 성별 등</li>
                        <li><strong>서비스 이용 시 자동 수집 항목:</strong> 접속 로그, IP 주소, 기기정보, 사진 메타데이터(촬영 일시, 위치 등), 업로드한 콘텐츠</li>
                        <li><strong>콘텐츠 분석을 위한 정보:</strong> 업로드한 일기 내용, 피드백, 감정 태그, 말투 설정</li>
                    </ul>
                    <h2>3. 개인정보의 처리 및 보유 기간</h2>
                    <ul>
                        <li>회원 가입 및 관리: 회원 탈퇴 시까지</li>
                        <li>부정 이용 방지를 위한 기록: 탈퇴 후 최대 1년</li>
                        <li>관련 법령에 따른 보관 기간
                            <ul>
                                <li>전자상거래법: 계약/결제/공급기록 5년, 분쟁처리 기록 3년</li>
                                <li>통신비밀보호법: 접속기록 3개월</li>
                                <li>전자금융거래법: 금융거래기록 5년</li>
                            </ul>
                        </li>
                    </ul>
                    <h2>4. 개인정보의 제3자 제공</h2>
                    <p>회사는 원칙적으로 이용자의 개인정보를 제3자에게 제공하지 않습니다. 다만, 다음의 경우에는 예외적으로 제공될 수 있습니다.</p>
                    <ul>
                        <li>이용자가 사전에 동의한 경우</li>
                        <li>법령에 근거한 경우</li>
                    </ul>
                    <p><strong>※ OpenAI에 제공되는 경우:</strong></p>
                    <ul>
                        <li>제공받는 자: OpenAI (<a href="https://openai.com/enterprise-privacy" target="_blank" rel="noopener noreferrer">https://openai.com/enterprise-privacy</a>)</li>
                        <li>제공 항목: 사진 기반 콘텐츠(일기), 말투 설정, 감정 태그 등</li>
                        <li>이용 목적: API 호출을 통한 AI 일기 초안 생성</li>
                        <li>보관/보호조치: OpenAI Enterprise 계정 사용, 모델 훈련에는 사용하지 않음</li>
                    </ul>
                    <h2>5. 개인정보 처리의 위탁</h2>
                    <p>회사는 다음과 같이 일부 업무를 외부에 위탁하고 있으며, 위탁계약 시 개인정보 보호법에 따라 관리·감독하고 있습니다.</p>
                    <table style="border: 1px solid black; border-collapse: collapse; width: 100%;">
                        <thead>
                            <tr>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">수탁업체명</th>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">위탁업무 내용</th>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">보관 국가</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">OpenAI</td>
                                <td style="border: 1px solid black; padding: 8px;">API 기반 AI 일기 생성</td>
                                <td style="border: 1px solid black; padding: 8px;">미국</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">Gemini</td>
                                <td style="border: 1px solid black; padding: 8px;">API 기반 AI 일기 생성</td>
                                <td style="border: 1px solid black; padding: 8px;">미국</td>
                            </tr>
                        </tbody>
                    </table>
                    <h2>6. 국외이전</h2>
                    <p>회사는 OpenAI API 사용과 관련하여 개인정보가 국외로 이전될 수 있음을 알려드립니다.</p>
                    <ul>
                        <li>이전 국가: 미국</li>
                        <li>이전 일시 및 방법: 사용자가 사진 업로드 시 실시간 API 요청을 통해 전송</li>
                        <li>이전 항목: 일기 콘텐츠, 사진 요약, 감정 태그 등</li>
                        <li>이전 목적: AI 일기 초안 생성</li>
                        <li>보유 및 이용 기간: 처리 즉시 폐기, 저장되지 않음</li>
                        <li>보호조치: OpenAI Enterprise 규약상 데이터 비훈련 보장</li>
                    </ul>
                    <h2>7. 개인정보의 파기</h2>
                    <p>회사는 개인정보의 보유기간이 경과하거나 처리목적이 달성된 경우 지체없이 파기합니다.</p>
                    <ul>
                        <li>전자적 파일: 복구 불가능한 기술적 방식으로 삭제</li>
                        <li>서면: 분쇄기 파쇄 또는 소각</li>
                    </ul>
                    <h2>8. 개인정보 자동 수집 장치 설치·운영 및 거부</h2>
                    <p>회사는 서비스 운영에 필요한 쿠키 등을 수집할 수 있습니다.</p>
                    <ul>
                        <li>자동 수집 항목: 접속기록, 이용기록, 기기정보</li>
                        <li>쿠키 거부 방법: 브라우저 설정에서 쿠키 저장 거부 가능</li>
                    </ul>
                    <h2>9. 정보주체의 권리와 행사방법</h2>
                    <p>이용자는 언제든지 개인정보의 열람, 정정, 삭제, 처리정지를 요청할 수 있습니다.</p>
                    <ul>
                        <li>요청 방법: 앱 내 메뉴 또는 이메일(teamdiarypic@gmail.com)</li>
                        <li>법정대리인을 통한 행사 가능</li>
                    </ul>
                    <h2>10. 개인정보 보호책임자</h2>
                    <table style="border: 1px solid black; border-collapse: collapse; width: 100%;">
                        <tbody>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px; width: 30%; text-align: left;">성명</td>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">[담당자 이름]</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">직책</td>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">개인정보 보호책임자</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">이메일</td>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">teamdiarypic@gmail.com</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">연락처</td>
                                <td style="border: 1px solid black; padding: 8px; text-align: left;">[전화번호 입력]</td>
                            </tr>
                        </tbody>
                    </table>
                    <h2>11. 개인정보의 안전성 확보조치</h2>
                    <ul>
                        <li>비밀번호 일방향 암호화 저장</li>
                        <li>접근 권한 최소화 및 내부 교육 실시</li>
                        <li>해킹 방지 시스템(CSRF, XSS 차단) 적용</li>
                        <li>개인정보 암호화 및 안전한 통신(SSL) 적용</li>
                    </ul>
                    <h2>12. 권익침해 구제방법</h2>
                    <p>이용자는 아래 기관을 통해 개인정보 침해에 대한 도움을 받을 수 있습니다.</p>
                    <ul>
                        <li>개인정보침해신고센터: 118, <a href="https://privacy.kisa.or.kr" target="_blank" rel="noopener noreferrer">https://privacy.kisa.or.kr</a></li>
                        <li>개인정보분쟁조정위원회: 1833-6972, <a href="https://www.kopico.go.kr" target="_blank" rel="noopener noreferrer">https://www.kopico.go.kr</a></li>
                        <li>대검찰청: 1301, <a href="https://www.spo.go.kr" target="_blank" rel="noopener noreferrer">https://www.spo.go.kr</a></li>
                        <li>경찰청: 182, <a href="https://ecrm.cyber.go.kr" target="_blank" rel="noopener noreferrer">https://ecrm.cyber.go.kr</a></li>
                    </ul>
                    <h2>13. 개인정보 처리방침의 변경</h2>
                    <p>본 방침은 2025.03.01부터 적용됩니다.</p>
                    <p>변경이 있을 경우 최소 7일 전(중대한 사항의 경우 30일 전) 사전 공지합니다.</p>
                    <p><strong>📌 공개 방법 안내</strong></p>
                    <ul>
                        <li>이 방침은 앱 내 [설정 > 개인정보처리방침] 메뉴에서 상시 열람 가능합니다.</li>
                        <li>첫 화면 또는 로그인 후 팝업을 통해 주요 변경 시 고지합니다.</li>
                    </ul>
                </body>
                """;
        createTermIfNotExists(TermsType.PRIVACY_POLICY, 1, TermsType.PRIVACY_POLICY.getDescription(), privacyPolicyContent, true, TermsType.PRIVACY_POLICY.getDisplayOrder());

        // 4. 개인정보 수집-이용 동의
        String personalInfoCollectionAgreementContent = """
                <head>
                    <meta charset="UTF-8">
                    <title>개인정보 수집-이용 동의</title>
                </head>
                <body>
                    <h1>[필수] 개인정보 수집-이용 동의</h1>
                    <p>📄 <strong>개인정보 수집 및 이용 동의서 (필수)</strong></p>
                    <p>본인은 ‘DiaryPic’ 서비스(이하 “서비스”라 함)를 기획 및 운영하는 <strong>서울시립대학교 컴퓨터과학과 aPPLY 프로젝트팀(이하 "개발자"라 함)</strong>이 아래와 같이 본인의 개인정보를 수집 및 이용하는 것에 충분히 숙지하였으며, 이에 따라 동의합니다.</p>
                    <h2>■ 수집 및 이용 목적</h2>
                    <table style="border: 1px solid black; border-collapse: collapse; width: 100%;">
                        <thead>
                            <tr>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">수집·이용 목적</th>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">수집·이용 항목</th>
                                <th style="border: 1px solid black; padding: 8px; text-align: left;">보유 및 이용기간</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">서비스 회원 가입 확인 및 계정 식별</td>
                                <td style="border: 1px solid black; padding: 8px;">SNS 간편 로그인 계정 정보(이메일 주소 등)</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">사진 기반 AI 일기 생성 기능 제공</td>
                                <td style="border: 1px solid black; padding: 8px;">업로드된 사진, 사진 메타데이터(촬영 일시, 장소 등), 사용자가 입력한 일기, 키워드, 말투 설정</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">일기 작성 및 캘린더/앨범 기능 운영</td>
                                <td style="border: 1px solid black; padding: 8px;">일기 작성 내용, 대표 이미지, 감정 이모티콘, 좋아요 태그 등</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">서비스 품질 개선 및 통계 분석</td>
                                <td style="border: 1px solid black; padding: 8px;">서비스 이용 기록(IP 주소, 디바이스 정보, 접속 로그, 불량 이용 기록 등)</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">AI 추천 및 자동화 기능 운영</td>
                                <td style="border: 1px solid black; padding: 8px;">사용자 입력 및 선택 정보 + 변환된 AI 추천 결과 데이터(예: 베스트샷 추천 결과 등)</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                            <tr>
                                <td style="border: 1px solid black; padding: 8px;">고객 문의 대응</td>
                                <td style="border: 1px solid black; padding: 8px;">이메일 주소</td>
                                <td style="border: 1px solid black; padding: 8px;">회원 탈퇴 시까지</td>
                            </tr>
                        </tbody>
                    </table>
                    <h2>■ 수집 방법</h2>
                    <ul>
                        <li>SNS 간편 로그인 시, 사용자 식별용 이메일을 수집합니다.</li>
                        <li>일기 작성 과정 중 사용자가 업로드한 사진, 작성 내용, 선택 항목 등을 수집합니다.</li>
                        <li>서비스 이용 중 자동으로 생성되는 기술 정보(IP, 로그, 디바이스 정보 등)를 수집할 수 있습니다.</li>
                    </ul>
                    <h2>■ 고지 사항</h2>
                    <ul>
                        <li>고객님은 위 동의를 거부할 권리가 있으나, 필수 항목에 대한 동의를 거부하실 경우 서비스 이용이 제한될 수 있습니다.</li>
                        <li>더 자세한 내용은 [개인정보 처리방침]에서 확인하실 수 있습니다.</li>
                    </ul>
                </body>
                """;
        createTermIfNotExists(TermsType.PERSONAL_INFO_COLLECTION_AGREEMENT, 1, TermsType.PERSONAL_INFO_COLLECTION_AGREEMENT.getDescription(), personalInfoCollectionAgreementContent, true, TermsType.PERSONAL_INFO_COLLECTION_AGREEMENT.getDisplayOrder());

        // 5. 마케팅 정보 수신 동의
        String marketingOptInContent = """
                <head>
                    <meta charset="UTF-8">
                    <title>마케팅 정보 수신 동의</title>
                </head>
                <body>
                    <h1>(선택) DiaryPic 혜택정보 제공을 위한 개인정보 수집 및 이용 동의</h1>
                    <h2>■ 개인정보 수집 및 이용 목적</h2>
                    <ul>
                        <li>이벤트 안내 및 추천</li>
                    </ul>
                    <h2>■ 수집 항목</h2>
                    <ul>
                        <li>로그인 시 수집된 이메일 주소 (SNS 계정 기반)</li>
                    </ul>
                    <h2>■ 보유 및 이용기간</h2>
                    <ul>
                        <li>회원 탈퇴 시까지</li>
                    </ul>
                    <p>본인은 상기 내용과 같이 귀사가 본인의 개인정보를 수집 및 이용함에 동의합니다.</p>
                    <p>※ 본 동의는 서비스 이용에 필수적이지 않으며, 거부하실 수 있습니다. 거부에 따른 불이익은 없습니다.</p>
                    <p>더 자세한 내용에 대해서는 [개인정보 처리방침]을 참고하시기 바랍니다.</p>
                </body>
                """;
        createTermIfNotExists(TermsType.MARKETING_OPT_IN, 1, TermsType.MARKETING_OPT_IN.getDescription(), marketingOptInContent, false, TermsType.MARKETING_OPT_IN.getDisplayOrder());

        log.info("약관 데이터 초기화 완료.");
    }

    private void createTermIfNotExists(TermsType termsType, int version, String title, String content, boolean required, int displayOrder) {
        if (!termsRepository.findByTermsTypeAndVersion(termsType, version).isPresent()) {
            if (!termsRepository.findFirstByTermsTypeOrderByVersionDesc(termsType).filter(t -> t.getVersion() > version).isPresent()) {
                Terms term = Terms.builder()
                        .termsType(termsType)
                        .version(version)
                        .title(title)
                        .content(content)
                        .required(required)
                        .effectiveDate(LocalDateTime.now())
                        .displayOrder(displayOrder)
                        .build();
                termsRepository.save(term);
                log.info("약관 '{}' (type: {}, v{}, order:{}) 저장 완료.", title, termsType.name(), version, displayOrder);
            } else {
                log.warn("약관 '{}' (type: {}, v{}) 보다 최신 버전이 이미 DB에 존재하여 삽입을 건너뜁니다.", title, termsType.name(), version);
            }
        } else {
            log.info("약관 '{}' (type: {}, v{}) (은)는 이미 정확히 일치하는 버전으로 DB에 존재합니다. (삽입 건너뜀)", title, termsType.name(), version);
        }
    }
}