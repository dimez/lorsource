<%@ page pageEncoding="utf-8" contentType="text/html; charset=utf-8"%>
<%--
  ~ Copyright 1998-2022 Linux.org.ru
  ~    Licensed under the Apache License, Version 2.0 (the "License");
  ~    you may not use this file except in compliance with the License.
  ~    You may obtain a copy of the License at
  ~
  ~        http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~    Unless required by applicable law or agreed to in writing, software
  ~    distributed under the License is distributed on an "AS IS" BASIS,
  ~    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~    See the License for the specific language governing permissions and
  ~    limitations under the License.
  --%>
<%@ taglib tagdir="/WEB-INF/tags" prefix="lor" %>
<%--@elvariable id="message" type="ru.org.linux.topic.Topic"--%>
<%--@elvariable id="preparedMessage" type="ru.org.linux.topic.PreparedTopic"--%>

<jsp:include page="/WEB-INF/jsp/head.jsp"/>

<title>Возврат в неподтвержденные</title>
<jsp:include page="/WEB-INF/jsp/header.jsp"/>

<h1>Возврат в неподтвержденные</h1>
Вы можете отменить подтверждение и вернуть топик в список неподтвержденных.
<form method=POST action="uncommit.jsp">
<lor:csrf/>
<input type=hidden name=msgid value="${message.id}">
<div class=messages>
  <lor:topic messageMenu="<%= null %>" preparedMessage="${preparedMessage}" message="${message}" showMenu="false"/>
</div>
<input type=submit value="Отменить подтверждение">
</form>
<jsp:include page="/WEB-INF/jsp/footer.jsp"/>
