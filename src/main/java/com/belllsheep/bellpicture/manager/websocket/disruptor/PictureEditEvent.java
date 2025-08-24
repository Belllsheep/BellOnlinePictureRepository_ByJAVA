package com.belllsheep.bellpicture.manager.websocket.disruptor;

import com.belllsheep.bellpicture.manager.websocket.model.PictureEditRequestMessage;
import com.belllsheep.bellpicture.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 消息编辑事件
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;
    
    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}
