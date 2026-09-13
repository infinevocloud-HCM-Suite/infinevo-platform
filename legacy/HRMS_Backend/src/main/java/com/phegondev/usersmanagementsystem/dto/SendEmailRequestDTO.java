package com.phegondev.usersmanagementsystem.dto;

import java.util.List;

public class SendEmailRequestDTO {
	
	
	    private List<UserSummaryDTO> to; 
	    private List<String> cc;         
	    private String messageBody;
	    
		public List<UserSummaryDTO> getTo() {
			return to;
		}
		public void setTo(List<UserSummaryDTO> to) {
			this.to = to;
		}
		public List<String> getCc() {
			return cc;
		}
		public void setCc(List<String> cc) {
			this.cc = cc;
		}
		public String getMessageBody() {
			return messageBody;
		}
		public void setMessageBody(String messageBody) {
			this.messageBody = messageBody;
		}
		public SendEmailRequestDTO() {
			super();
			// TODO Auto-generated constructor stub
		}
		public SendEmailRequestDTO(List<UserSummaryDTO> to, List<String> cc, String messageBody) {
			super();
			this.to = to;
			this.cc = cc;
			this.messageBody = messageBody;
		}
		@Override
		public String toString() {
			return "SendEmailRequestDTO [to=" + to + ", cc=" + cc + ", messageBody=" + messageBody + "]";
		} 
	    
	    

}
