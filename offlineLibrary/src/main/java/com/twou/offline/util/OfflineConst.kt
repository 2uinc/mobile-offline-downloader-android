package com.twou.offline.util

object OfflineConst {

    val OFFLINE_VIDEO_SCRIPT = """
        <script>
            function getPos(el) {
                for (var lx=0, ly=0; el != null; lx += el.offsetLeft, ly += el.offsetTop, el = el.offsetParent);
                return {x: lx,y: ly};
            }
             
            function getVideoPositionAndNotify(element) {
                var src = element.getAttribute("src");
                var id = element.getAttribute("id");
                var subtitleUrl = element.getAttribute("subtitle_url");
                var pos = getPos(element);
                
                console.log("" + src + " " + subtitleUrl + " " + pos.x + " " + pos.y);
                javascript:window.offline.onProcessVideoPlayer(src, subtitleUrl, id, pos.x, pos.y);
            };
            
            function findAllVideoPlayers() {
                var players = document.getElementsByClassName("offline-video-player");
                for (var i = 0; i < players.length; i++) {
                    getVideoPositionAndNotify(players[i]);
                }
            };
            
            window.addEventListener('load', function () {
                window.addEventListener('resize', function(e) {
                    findAllVideoPlayers();  
                });
                
                findAllVideoPlayers(); 
            });
            
        </script>    
    """.trimIndent()

    const val PRINT_HTML =
        "javascript:window.android.print(document.getElementsByTagName('html')[0].innerHTML, document.location.href);"

    const val PROGRESS_CSS = """
        <style>
            .offline-video-privacy-error{
              position: relative;
              width: 50px;
              height: 50px;
              top: 50%;
              left: 50%;
              transform: translate(-50%,-50%);
            }
            
            .offline-video-preview-loader{
              position: relative;
              width: 100px;
              height: 100px;
              top: 50%;
              left: 50%;
              transform: translate(-50%,-50%);
            }
            
            .offline-video-preview-circular{
              animation: rotate 2s linear infinite;
              height: 100px;
              position: relative;
              width: 100px;
            }
            
            .offline-video-preview-path {
              stroke-dasharray: 1,200;
              stroke-dashoffset: 0;
              stroke:#B6463A;
              animation:
               dash 1.5s ease-in-out infinite,
               color 6s ease-in-out infinite;
              stroke-linecap: round;
            }
            
            @keyframes rotate{
             100%{
              transform: rotate(360deg);
             }
            }
            @keyframes dash{
             0%{
              stroke-dasharray: 1,200;
              stroke-dashoffset: 0;
             }
             50%{
              stroke-dasharray: 89,200;
              stroke-dashoffset: -35;
             }
             100%{
              stroke-dasharray: 89,200;
              stroke-dashoffset: -124;
             }
            }
            @keyframes color{
              100%, 0%{
                stroke: #d62d20;
              }
              40%{
                stroke: #0057e7;
              }
              66%{
                stroke: #008744;
              }
              80%, 90%{
                stroke: #ffa700;
              }
            } 
        </style>
        """
}