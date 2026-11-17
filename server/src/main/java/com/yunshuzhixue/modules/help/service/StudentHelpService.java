package com.yunshuzhixue.modules.help.service;

import com.yunshuzhixue.modules.sysconfig.api.HelpDirectory;
import java.util.List;
import org.springframework.stereotype.Service;

/** 帮助中心（需求 5.11，S-HLP-03）：常见问题与客服联系方式均由 M03 维护 */
@Service
public class StudentHelpService {

    private final HelpDirectory directory;

    public StudentHelpService(HelpDirectory directory) {
        this.directory = directory;
    }

    public HelpView load() {
        List<FaqItem> faqs = directory.faqs().stream()
                .map(faq -> new FaqItem(faq.id(), faq.question(), faq.answer()))
                .toList();
        HelpDirectory.Contact contact = directory.contact();
        return new HelpView(faqs,
                new ContactView(contact.wechatQrcodeUrl(), contact.email(), contact.phone()));
    }

    public record HelpView(List<FaqItem> faqs, ContactView contact) {
    }

    public record FaqItem(Long id, String question, String answer) {
    }

    public record ContactView(String wechatQrcodeUrl, String email, String phone) {
    }
}
