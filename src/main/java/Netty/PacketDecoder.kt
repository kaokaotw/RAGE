/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License version 3
 as published by the Free Software Foundation. You may not use, modify
 or distribute this program under any other version of the
 GNU Affero General Public License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package Netty

import EventID
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import java.util.*

class PacketDecoder : ByteToMessageDecoder() {
    override fun decode(chc: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val c = chc.channel().attr(NettyClient.CLIENT_KEY).get() ?: return

        if (c.nDecodeLen == -1) {
            if (buffer.readableBytes() < 4) {
                return
            }

            var L = buffer.readIntLE()
            val packetnum = buffer.readIntLE() // packetnum

            c.nDecodeLen = L - 32
//            println("接收整體長度: " + c.nDecodeLen + " | packetNum: $packetnum")
        }

        if (buffer.readableBytes() >= (c.nDecodeLen + 24)) {
            val prefix = buffer.readBytes(2) // prefix (頻道副端口TcpRelay(9301) 只到這邊 然後不用加解密)
//            println("prefix " + Util.readableByteArrayFromByteBuf(prefix))

            buffer.readIntLE() // packetnum
            val iv = ByteArray(8)
            buffer.readBytes(iv)

            var dec = ByteArray(c.nDecodeLen/*L - 32*/)
            buffer.readBytes(dec)

            buffer.skipBytes(HMAC_SIZE) // 10

            val inPacket = InPacket(DESCrypt.decrypt(c.packetKey, iv, dec))
            val dwPerformerID = inPacket.decodeInt() // 00 00 00 00 如果 a = 1 的話是 00 00 01 01
            inPacket.SerializeHelperSize = inPacket.decodeInt()
            for (i in 0 until inPacket.SerializeHelperSize) {
                val SerializeHelperUnk = inPacket.decodeLong()
//                println("SerializeHelperUnk: $SerializeHelperUnk")
            }
            val m_anTrace = inPacket.decodeLong() // -1
            val m_anTrace1 = inPacket.decodeLong() // -1
//            println("dwPerformerID: $dwPerformerID SerializeHelperSize: " + inPacket.SerializeHelperSize + " m_anTrace: $m_anTrace m_anTrace1: $m_anTrace1")

            val op = inPacket.decodeShort().toInt()
            val length = inPacket.decodeInt()
            inPacket.header = op
            inPacket.length = length
            if (length > 0) inPacket.compress = inPacket.decodeBoolean()
//            EventID.reload()
            val ID = EventID.getEventIDByOp(op)
            if (!EventID.isSpam(ID)) println(
                String.format(
                    "[In]\t| %s, %d/0x%s\t| %s",
                    if (ID == null) "未知" else ID,
                    op,
                    Integer.toHexString(op).uppercase(Locale.getDefault()),
                    inPacket
                )
            )
            c.nDecodeLen = -1;
            out.add(inPacket)
        }
    }
}